package com.isap.domain;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.stream.Collectors;

public record Poll(String pollId, String question, Map<String, String> options) {

    public static final String SK_POLL = "poll";

    public Map<String, AttributeValue> toDynamoDbItem() {
        Map<String, AttributeValue> optionsMap = options.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> AttributeValue.builder().s(entry.getValue()).build()
                ));

        return Map.of(
                "PK", AttributeValue.builder().s(pollId).build(),
                "SK", AttributeValue.builder().s(SK_POLL).build(),
                "question", AttributeValue.builder().s(question).build(),
                "options", AttributeValue.builder().m(optionsMap).build()
        );
    }

    public static Poll fromDynamoDbItem(Map<String, AttributeValue> item) {
        String pollId = item.get("PK").s();
        String question = item.get("question").s();

        Map<String, String> options = item.get("options").m()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().s()
                ));

        return new Poll(pollId, question, options);
    }
}
