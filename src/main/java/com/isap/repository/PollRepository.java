package com.isap.repository;

import com.isap.domain.Option;
import com.isap.domain.Poll;
import com.isap.domain.Vote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
public class PollRepository {

    private static final String TABLE_NAME = "PollTable";

    private final DynamoDbClient dynamoDbClient;

    @Inject
    public PollRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public GetItemResponse getPollByPollId(String pollId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.builder().s(pollId).build(),
                        "SK", AttributeValue.builder().s(SK_POLL).build()
                ))
                .build();

        return dynamoDbClient.getItem(request);
    }

    public boolean incrementVoteCount(String pollId, String optionId) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME) // DynamoDB table name
                .key(Map.of(
                        "PK", AttributeValue.builder().s(optionId).build(),
                        "SK", AttributeValue.builder().s(SK_OPTION).build()
                ))
                .updateExpression("SET VoteCount = VoteCount + :increment")
                .expressionAttributeValues(Map.of(
                        ":increment", AttributeValue.builder().n("1").build()
                ))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);

        if (updateItemResponse.sdkHttpResponse().isSuccessful())
            return putVote(pollId, optionId);

        return false;
    }

    private boolean putVote(String pollId, String optionId) {
        // Insert the poll question into DynamoDB
        PutItemRequest votePutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(new Vote(UUID.randomUUID().toString(), pollId, optionId, Instant.now().toString())
                        .toDynamoDbItem())
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        PutItemResponse putItemResponse = dynamoDbClient.putItem(votePutRequest);

        return putItemResponse.sdkHttpResponse().isSuccessful();
    }

    public QueryResponse getVotesByPollId(String pollId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1")  // Using GSI1 for querying
                .keyConditionExpression("GSI1PK = :pollId AND GSI1SK = :SK")
                .expressionAttributeValues(Map.of(
                        ":pollId", AttributeValue.builder().s(pollId).build(),
                        ":SK", AttributeValue.builder().s(SK_VOTE).build()))
                .build();

        return dynamoDbClient.query(queryRequest);
    }

    public QueryResponse getOptionsByPollId(String pollId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :pollId AND GSI1SK = :SK")
                .expressionAttributeValues(Map.of(
                        ":pollId", AttributeValue.builder().s(pollId).build(),
                        ":SK", AttributeValue.builder().s(SK_OPTION).build()))
                .build();

        return dynamoDbClient.query(queryRequest);
    }

    public boolean createPoll(Map<String, List<String>> newPollData) {
        if (newPollData.size() != 1) {
            throw new IllegalArgumentException("New poll data should have exactly one entry");
        }

        // Extract poll question and options from the newPollData map
        String pollId = UUID.randomUUID().toString();
        String question = newPollData.keySet().iterator().next();
        List<String> options = newPollData.get(question);

        // Create the map of UUIDs to options
        Map<String, String> optionsMap = options.stream()
                .collect(Collectors.toMap(option -> UUID.randomUUID().toString(), option -> option));

        // Insert the poll question into DynamoDB
        PutItemRequest pollPutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(new Poll(pollId, question, optionsMap)
                        .toDynamoDbItem())
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        PutItemResponse putPollItemResponse = dynamoDbClient.putItem(pollPutRequest);

        List<PutItemResponse> putOptionItemResponses = new ArrayList<>();
        optionsMap.forEach((key, value) -> {
            PutItemRequest optionPutRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(new Option(key, pollId, value, 0)
                            .toDynamoDbItem())
                    .build();

            putOptionItemResponses.add(dynamoDbClient.putItem(optionPutRequest));
        });

        return putPollItemResponse.sdkHttpResponse().isSuccessful() &&
                (putOptionItemResponses.size() == optionsMap.size());
    }
}
