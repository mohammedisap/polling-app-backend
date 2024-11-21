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

import static com.isap.domain.Poll.fromDynamoDbItem;
import static com.isap.domain.Option.fromQueryResponse;

@Path("/poll")
@Slf4j
@ApplicationScoped
public class PollService {

    private final PollRepository pollRepository;

    @Inject
    public PollService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPoll(@QueryParam("pollId") String pollId) {
        if (pollId == null || pollId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        GetItemResponse response = pollRepository.getPollByPollId(pollId);

        if (response == null || response.item().isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Poll not found").build();
        }

        Poll poll = fromDynamoDbItem(response.item());

        Map<String, Object> result = new HashMap<>();
        result.put("pollId", poll.pollId());
        result.put("question", poll.question());

        if (poll.options().size() > 7 || poll.options().size() < 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(String.format("Poll has an invalid number of options: %s", poll.options().size()))
                    .build();
        }

        result.put("options", poll.options());

        return Response.ok(result).build();
    }

    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response vote(Map<String, String> requestBody) {
        String pollId = requestBody.get("pollId");
        String optionId = requestBody.get("optionId");

        if (pollId == null || pollId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        if (optionId == null || optionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Option ID is required").build();
        }

        try {
            if (pollRepository.incrementVoteCount(pollId, optionId)) {
                return Response.ok(getOptionsByPollId(pollId)).build();
            } else {
                log.error("Error while updating vote count in DynamoDB.");
            }
        } catch (DynamoDbException e) {
            log.error("Error while updating vote count in DynamoDB: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to update the vote count. Please try again").build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Failed to update the vote count. Please try again")
                .build();
    }

    private Response getOptionsByPollId(String pollId) {
        if (pollId == null || pollId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll ID is required").build();
        }

        QueryResponse response = pollRepository.getOptionsByPollId(pollId);

        return Response.ok(fromQueryResponse(response)).build();
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPoll(Map<String, Object> newPollData) {
        if (!newPollData.containsKey("question") ||
                !(newPollData.get("question") instanceof String question)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll question is required and should be a string").build();
        }
        if (!newPollData.containsKey("options") ||
                !(newPollData.get("options") instanceof List)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Poll options are required and should be a list").build();
        }

        try {
            Map<String, List<String>> pollDataToPut = new HashMap<>();
            newPollData.put(question, newPollData.get("options"));

            if (pollRepository.createPoll(pollDataToPut)) {
                return Response.status(Response.Status.CREATED).entity("Poll created successfully").build();
            }
        } catch (DynamoDbException e) {
            log.error("Error creating poll: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error creating poll").build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error creating poll")
                .build();

    }

    @GET
    @Path("/votes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPollVotes(@QueryParam("pollId") String pollId) {
        QueryResponse response = pollRepository.getVotesByPollId(pollId);

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
