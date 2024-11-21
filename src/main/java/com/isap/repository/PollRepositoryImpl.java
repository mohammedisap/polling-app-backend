package com.isap.repository;

import com.isap.domain.Vote;
import com.isap.exception.NotFoundException;
import com.isap.utils.DynamoDbHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.isap.domain.Option.SK_OPTION;
import static com.isap.domain.Poll.SK_POLL;
import static com.isap.domain.Vote.SK_VOTE;

@ApplicationScoped
@Slf4j
public class PollRepositoryImpl implements PollRepository {

    private final DynamoDbHelper dynamoDbHelper;

    @Inject
    public PollRepositoryImpl(DynamoDbHelper dynamoDbHelper) {
        this.dynamoDbHelper = dynamoDbHelper;
    }

    @Override
    public GetItemResponse getPollByPollId(String pollId) {
        log.debug("Requesting Poll with ID: {}", pollId);

        if (pollId == null || pollId.isEmpty()) {
            log.warn("Poll ID is missing or empty");
            throw new IllegalArgumentException("Poll ID cannot be null or empty");
        }

        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s(pollId).build(),
                "SK", AttributeValue.builder().s(SK_POLL).build()
        );

        GetItemResponse response = dynamoDbHelper.getItem(key);

        if (response == null || response.item().isEmpty()) {
            log.warn("Poll not found for PollID: {}", pollId);
            throw new NotFoundException("Poll not found with ID: " + pollId);
        }

        log.debug("Poll retrieved: {}", response.item());
        return response;
    }

    @Override
    public boolean incrementVoteCount(String pollId, String optionId) {
        log.debug("Incrementing vote count for PollID: {} and OptionID: {}", pollId, optionId);

        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s(optionId).build(),
                "SK", AttributeValue.builder().s(SK_OPTION).build()
        );

        Map<String, AttributeValue> values = Map.of(":increment", AttributeValue.builder().n("1").build());
        String updateExpression = "SET VoteCount = VoteCount + :increment";

        UpdateItemResponse updateItemResponse = dynamoDbHelper.updateItem(key, updateExpression, values);

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

        Map<String, AttributeValue> item = new Vote(UUID.randomUUID().toString(), pollId, optionId, Instant.now().toString()).toDynamoDbItem();
        PutItemResponse putItemResponse = dynamoDbHelper.putItem(item);

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

        Map<String, AttributeValue> expressionValues = Map.of(
                ":pollId", AttributeValue.builder().s(pollId).build(),
                ":SK", AttributeValue.builder().s(SK_VOTE).build()
        );

        return dynamoDbHelper.queryItems("GSI1", "GSI1PK = :pollId AND GSI1SK = :SK", expressionValues);
    }

    @Override
    public QueryResponse getOptionsByPollId(String pollId) {
        log.debug("Querying options for PollID: {}", pollId);

        Map<String, AttributeValue> expressionValues = Map.of(
                ":pollId", AttributeValue.builder().s(pollId).build(),
                ":SK", AttributeValue.builder().s(SK_OPTION).build()
        );

        return dynamoDbHelper.queryItems("GSI1", "GSI1PK = :pollId AND GSI1SK = :SK", expressionValues);
    }

    @Override
    public boolean createPoll(Map<String, List<String>> newPollData) {
        log.info("Creating a new poll with data: {}", newPollData);

        if (newPollData.size() != 1) {
            log.error("Invalid poll data format. Expected exactly one entry, but got: {}", newPollData.size());
            throw new IllegalArgumentException("New poll data should have exactly one entry");
        }

        return dynamoDbHelper.createPollAndOptions(newPollData);
    }
}