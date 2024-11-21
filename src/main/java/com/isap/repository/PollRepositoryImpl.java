package com.isap.repository;

import com.isap.domain.Option;
import com.isap.domain.Poll;
import com.isap.domain.Vote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.isap.domain.Option.SK_OPTION;
import static com.isap.domain.Poll.SK_POLL;
import static com.isap.domain.Vote.SK_VOTE;

@ApplicationScoped
@Slf4j
public class PollRepositoryImpl implements PollRepository {

    private static final String TABLE_NAME = "PollTable";
    private final DynamoDbClient dynamoDbClient;

    @Inject
    public PollRepositoryImpl(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public GetItemResponse getPollByPollId(String pollId) {
        log.debug("Requesting Poll with ID: {}", pollId);

        if (pollId == null || pollId.isEmpty()) {
            log.warn("Poll ID is missing or empty");
            return null;
        }

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("PK", AttributeValue.builder().s(pollId).build(),
                        "SK", AttributeValue.builder().s(SK_POLL).build()))
                .build();

        log.debug("GetItemRequest: {}", request);
        GetItemResponse response = dynamoDbClient.getItem(request);

        if (response == null || response.item().isEmpty()) {
            log.warn("Poll not found for PollID: {}", pollId);
        } else {
            log.debug("Poll retrieved: {}", response.item());
        }

        return response;
    }

    @Override
    public boolean incrementVoteCount(String pollId, String optionId) {
        log.debug("Incrementing vote count for PollID: {} and OptionID: {}", pollId, optionId);

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("PK", AttributeValue.builder().s(optionId).build(),
                        "SK", AttributeValue.builder().s(SK_OPTION).build()))
                .updateExpression("SET VoteCount = VoteCount + :increment")
                .expressionAttributeValues(Map.of(":increment", AttributeValue.builder().n("1").build()))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        log.debug("UpdateItemRequest: {}", updateItemRequest);
        UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);

        if (updateItemResponse.sdkHttpResponse().isSuccessful()) {
            log.info("Vote count incremented successfully for OptionID: {}", optionId);
            return putVote(pollId, optionId);
        } else {
            log.error("Failed to increment vote count for OptionID: {}. Response: {}", optionId, updateItemResponse.sdkHttpResponse());
        }

        return false;
    }

    private boolean putVote(String pollId, String optionId) {
        log.debug("Inserting vote for PollID: {} and OptionID: {}", pollId, optionId);

        PutItemRequest votePutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(new Vote(UUID.randomUUID().toString(), pollId, optionId, Instant.now().toString()).toDynamoDbItem())
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        PutItemResponse putItemResponse = dynamoDbClient.putItem(votePutRequest);

        if (putItemResponse.sdkHttpResponse().isSuccessful()) {
            log.info("Vote inserted successfully for PollID: {} and OptionID: {}", pollId, optionId);
            return true;
        } else {
            log.error("Failed to insert vote for PollID: {} and OptionID: {}. Response: {}", pollId, optionId, putItemResponse.sdkHttpResponse());
        }

        return false;
    }

    @Override
    public QueryResponse getVotesByPollId(String pollId) {
        log.debug("Querying votes for PollID: {}", pollId);

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :pollId AND GSI1SK = :SK")
                .expressionAttributeValues(Map.of(":pollId", AttributeValue.builder().s(pollId).build(),
                        ":SK", AttributeValue.builder().s(SK_VOTE).build()))
                .build();

        log.debug("QueryRequest: {}", queryRequest);
        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (response.items().isEmpty()) {
            log.warn("No votes found for PollID: {}", pollId);
        } else {
            log.debug("Votes retrieved for PollID: {}: {}", pollId, response.items());
        }

        return response;
    }

    @Override
    public QueryResponse getOptionsByPollId(String pollId) {
        log.debug("Querying options for PollID: {}", pollId);

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :pollId AND GSI1SK = :SK")
                .expressionAttributeValues(Map.of(":pollId", AttributeValue.builder().s(pollId).build(),
                        ":SK", AttributeValue.builder().s(SK_OPTION).build()))
                .build();

        log.debug("QueryRequest: {}", queryRequest);
        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (response.items().isEmpty()) {
            log.warn("No options found for PollID: {}", pollId);
        } else {
            log.debug("Options retrieved for PollID: {}: {}", pollId, response.items());
        }

        return response;
    }

    @Override
    public boolean createPoll(Map<String, List<String>> newPollData) {
        log.info("Creating a new poll with data: {}", newPollData);

        if (newPollData.size() != 1) {
            log.error("Invalid poll data format. Expected exactly one entry, but got: {}", newPollData.size());
            throw new IllegalArgumentException("New poll data should have exactly one entry");
        }

        String pollId = UUID.randomUUID().toString();
        String question = newPollData.keySet().iterator().next();
        List<String> options = newPollData.get(question);

        log.debug("Poll Question: {}, Options: {}", question, options);

        Map<String, String> optionsMap = options.stream()
                .collect(Collectors.toMap(option -> UUID.randomUUID().toString(), option -> option));

        PutItemRequest pollPutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(new Poll(pollId, question, optionsMap).toDynamoDbItem())
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        log.debug("Creating poll request: {}", pollPutRequest);

        PutItemResponse putPollItemResponse = dynamoDbClient.putItem(pollPutRequest);

        if (!putPollItemResponse.sdkHttpResponse().isSuccessful()) {
            log.error("Failed to create poll. Poll Question: {}", question);
            return false;
        }

        List<PutItemResponse> putOptionItemResponses = new ArrayList<>();
        optionsMap.forEach((key, value) -> {
            PutItemRequest optionPutRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(new Option(key, pollId, value, 0).toDynamoDbItem())
                    .build();

            log.debug("Creating Option with Id: {}, Value: {}", key, value);
            putOptionItemResponses.add(dynamoDbClient.putItem(optionPutRequest));
        });

        boolean allOptionsCreated = putOptionItemResponses.stream()
                .allMatch(response -> response.sdkHttpResponse().isSuccessful());

        if (allOptionsCreated) {
            log.info("Poll and options created successfully for PollID: {}", pollId);
        } else {
            log.error("Failed to create all options for PollID: {}", pollId);
        }

        return putPollItemResponse.sdkHttpResponse().isSuccessful() && allOptionsCreated;
    }
}
