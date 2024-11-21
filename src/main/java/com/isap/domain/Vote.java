package com.isap.domain;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.isap.domain.Option.SK_OPTION;

public record Vote(String voteId, String pollId, String optionId, String timestamp) {

    public static final String SK_VOTE = "vote";

    public Map<String, AttributeValue> toDynamoDbItem() {
        return Map.of(
                "PK", AttributeValue.builder().s(UUID.randomUUID().toString()).build(),
                "SK", AttributeValue.builder().s(SK_VOTE).build(),
                "GSI1PK", AttributeValue.builder().s(pollId).build(),
                "GSI1SK", AttributeValue.builder().s(SK_VOTE).build(),
                "GSI2PK", AttributeValue.builder().s(optionId).build(),
                "GSI2SK", AttributeValue.builder().s(SK_OPTION).build(),
                "pollId", AttributeValue.builder().s(pollId).build(),
                "optionId", AttributeValue.builder().s(optionId).build(),
                "timestamp", AttributeValue.builder().s(Instant.now().toString()).build()
        );
    }
}
