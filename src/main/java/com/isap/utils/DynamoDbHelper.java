package com.isap.utils;

import com.isap.exception.DatabaseException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.isap.domain.Poll;
import com.isap.domain.Option;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamoDbHelper {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbHelper(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public GetItemResponse getItem(String tableName, Map<String, AttributeValue> key) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        return dynamoDbClient.getItem(request);
    }

    public UpdateItemResponse updateItem(String tableName, Map<String, AttributeValue> key, String updateExpression, Map<String, AttributeValue> values) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpression)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        return dynamoDbClient.updateItem(request);
    }

    public PutItemResponse putItem(String tableName, Map<String, AttributeValue> item) {
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        return dynamoDbClient.putItem(request);
    }

    public QueryResponse queryItems(String tableName, String indexName, String keyConditionExpression, Map<String, AttributeValue> expressionValues) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(indexName)
                .keyConditionExpression(keyConditionExpression)
                .expressionAttributeValues(expressionValues)
                .build();

        return dynamoDbClient.query(request);
    }

    public TransactWriteItemsResponse transactWriteItems(TransactWriteItemsRequest request) {
        return dynamoDbClient.transactWriteItems(request);
    }

    // New method to handle the poll and options creation transaction
    public boolean createPollAndOptions(String tableName, Map<String, List<String>> newPollData) {
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

        // Build transaction request
        TransactWriteItemsRequest.Builder transactionRequest = TransactWriteItemsRequest.builder();

        Map<String, AttributeValue> pollItem = new Poll(pollId, question, optionsMap).toDynamoDbItem();
        transactionRequest.transactItems(TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(pollItem)
                        .build())
                .build());

        optionsMap.forEach((key, value) -> {
            Map<String, AttributeValue> optionItem = new Option(key, pollId, value, 0).toDynamoDbItem();
            transactionRequest.transactItems(TransactWriteItem.builder()
                    .put(Put.builder()
                            .tableName(tableName)
                            .item(optionItem)
                            .build())
                    .build());
        });

        try {
            TransactWriteItemsResponse response = dynamoDbClient.transactWriteItems(transactionRequest.build());
            log.info("Poll and options created successfully for PollID: {}", pollId);
            return response.sdkHttpResponse().isSuccessful();
        } catch (DynamoDbException e) {
            log.error("Transaction failed for PollID: {} with error: {}", pollId, e.awsErrorDetails().errorMessage());
            throw new DatabaseException("Failed to create poll and options in a transaction: " + e.getMessage(), e);
        }
    }
}