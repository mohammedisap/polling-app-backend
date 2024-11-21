package com.isap.domain;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Option(String optionId, String pollId, String text, int votes) {

    public static final String SK_OPTION = "option";

    public Map<String, AttributeValue> toDynamoDbItem() {
        return Map.of(
                "PK", AttributeValue.builder().s(optionId).build(),
                "SK", AttributeValue.builder().s(SK_OPTION).build(),
                "GSI1PK", AttributeValue.builder().s(pollId).build(),
                "GSI1SK", AttributeValue.builder().s(SK_OPTION).build(),
                "pollId", AttributeValue.builder().s(pollId).build(),
                "text",  AttributeValue.builder().s(text).build(),
                "votes", AttributeValue.builder().n("0").build()  // Initial vote count is 0
        );
    }

    public static List<Option> fromQueryResponse(QueryResponse queryResponse) {
        return queryResponse.items().stream()
                .map(item -> {
                    int voteCount = Integer.parseInt(item.get("votes").n());

                    return new Option(
                            item.get("PK").s(),
                            item.get("SK").s(),
                            item.get("text").s(),
                            voteCount
                    );
                })
                .collect(Collectors.toList());
    }
}