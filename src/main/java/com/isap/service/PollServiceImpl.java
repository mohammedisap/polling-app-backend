package com.isap.service;

import com.isap.domain.Poll;
import com.isap.repository.PollRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.isap.domain.Option.fromQueryResponse;
import static com.isap.domain.Poll.fromDynamoDbItem;

@Path("/poll")
@Slf4j
@ApplicationScoped
public class PollServiceImpl implements PollService {

    private final PollRepository pollRepository;

    @Inject
    public PollServiceImpl(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response getPoll(@QueryParam("pollId") String pollId) {
        log.info("Received request to get poll with pollId: {}", pollId);

        if (pollId == null || pollId.isEmpty()) {
            log.warn("Poll ID is missing or empty");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        log.debug("Fetching poll data for pollId: {}", pollId);
        GetItemResponse response = pollRepository.getPollByPollId(pollId);

        if (response == null || response.item().isEmpty()) {
            log.warn("Poll not found for pollId: {}", pollId);
            return Response.status(Response.Status.NOT_FOUND).entity("Poll not found").build();
        }

        Poll poll = fromDynamoDbItem(response.item());
        log.info("Poll retrieved successfully: {}", poll);

        Map<String, Object> result = new HashMap<>();
        result.put("pollId", poll.pollId());
        result.put("question", poll.question());

        if (poll.options().size() > 7 || poll.options().size() < 2) {
            log.error("Poll size out of bounds: {}", poll.options().size());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(String.format("Poll has an invalid number of options: %s", poll.options().size()))
                    .build();
        }

        result.put("options", poll.options());
        log.debug("Poll options: {}", poll.options());

        return Response.ok(result).build();
    }

    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Override
    public Response vote(Map<String, String> requestBody) {
        String pollId = requestBody.get("pollId");
        String optionId = requestBody.get("optionId");

        if (pollId == null || pollId.isEmpty()) {
            log.warn("Poll ID is missing or empty");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        if (optionId == null || optionId.isEmpty()) {
            log.warn("Option ID is missing or empty");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Option ID is required").build();
        }

        log.info("Received vote request for pollId: {} and optionId: {}", pollId, optionId);

        try {
            log.debug("Attempting to increment vote count for pollId: {} and optionId: {}", pollId, optionId);
            if (pollRepository.incrementVoteCount(pollId, optionId)) {
                log.info("Vote count updated successfully for pollId: {} and optionId: {}", pollId, optionId);
                return Response.ok(getOptionsByPollId(pollId)).build();
            } else {
                log.error("Failed to update vote count in DynamoDB for pollId: {} and optionId: {}", pollId, optionId);
            }
        } catch (DynamoDbException e) {
            log.error("Error while updating vote count in DynamoDB: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to update the vote count. Please try again").build();
        }

        log.warn("Failed to update vote count for pollId: {} and optionId: {}", pollId, optionId);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Failed to update the vote count. Please try again")
                .build();
    }

    private Response getOptionsByPollId(String pollId) {
        log.debug("Fetching options with vote stat for pollId: {}", pollId);

        if (pollId == null || pollId.isEmpty()) {
            log.warn("Poll ID is missing or empty");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        QueryResponse response = pollRepository.getOptionsByPollId(pollId);
        log.debug("Options fetched for pollId: {}: {}", pollId, response.items());

        return Response.ok(fromQueryResponse(response)).build();
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response createPoll(Map<String, Object> newPollData) {
        log.info("Received request to create a new poll: {}", newPollData);

        if (!newPollData.containsKey("question") ||
                !(newPollData.get("question") instanceof String question)) {
            log.warn("Poll question is missing or invalid: {}", newPollData.get("question"));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll question is required and should be a string").build();
        }

        if (!newPollData.containsKey("options") ||
                !(newPollData.get("options") instanceof List)) {
            log.warn("Poll options are missing or invalid: {}", newPollData.get("options"));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll options are required and should be a list").build();
        }

        try {
            Map<String, List<String>> pollDataToPut = new HashMap<>();
            newPollData.put(question, newPollData.get("options"));

            log.debug("Attempting to create a new poll with data: {}", newPollData);
            if (pollRepository.createPoll(pollDataToPut)) {
                log.info("Poll created successfully");
                return Response.status(Response.Status.CREATED).entity("Poll created successfully").build();
            }
        } catch (DynamoDbException e) {
            log.error("Error creating poll: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error creating poll").build();
        }

        log.error("Failed to create poll with data: {}", newPollData);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error creating poll")
                .build();
    }

    @GET
    @Path("/votes")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response getPollVotes(@QueryParam("pollId") String pollId) {
        log.info("Received request to get votes for pollId: {}", pollId);

        QueryResponse response = pollRepository.getVotesByPollId(pollId);
        log.debug("Votes fetched for pollId: {}: {}", pollId, response.items());

        List<Map<String, Object>> jsonResponse = response.items()
                .stream()
                .map(item -> {
                    Map<String, Object> jsonItem = new HashMap<>();
                    jsonItem.put("optionId", item.get("optionId").s());
                    jsonItem.put("voteId", item.get("PK").s());
                    jsonItem.put("timestamp", item.get("timestamp").s());
                    return jsonItem;
                })
                .collect(Collectors.toList());

        return Response.ok(jsonResponse).build();
    }
}
