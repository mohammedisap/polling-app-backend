package com.isap.repository;

import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;

public interface PollRepository {
    GetItemResponse getPollByPollId(String pollId);

    boolean incrementVoteCount(String pollId, String optionId);

    QueryResponse getVotesByPollId(String pollId);

    QueryResponse getOptionsByPollId(String pollId);

    boolean createPoll(Map<String, List<String>> newPollData);
}
