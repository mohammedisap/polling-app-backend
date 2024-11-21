package com.isap.service;

import com.isap.domain.Poll;
import com.isap.repository.PollRepository;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class PollServiceTest {

    private final PollRepository pollRepository = mock(PollRepository.class);
    private final PollService pollService = new PollService(pollRepository);

    private static final String POLL_ID = "poll1";
    private static final String QUESTION = "What is your favorite programming language?";
    private static final String OPTION_ID = "op1";
    private static final String OPTION_ID2 = "op2";
    private static final String INVALID_OPTION_ID = "invalid";

    private Map<String, AttributeValue> createPollItem() {
        Map<String, String> options = Map.of(
                OPTION_ID, "Java",
                OPTION_ID2, "Python"
        );

        Poll poll = new Poll(POLL_ID, QUESTION, options);

        return poll.toDynamoDbItem();
    }

    // Helper method to create mock UpdateItemResponse
    private UpdateItemResponse createMockVoteUpdateResponse() {
        return UpdateItemResponse.builder()
                .attributes(Map.of("VoteCount", AttributeValue.builder().n("1").build()))
                .build();
    }

    @Test
    public void testGetPoll_success() {
        //given
        GetItemResponse mockResponse = GetItemResponse.builder().item(createPollItem()).build();
        when(pollRepository.getPollByPollId(POLL_ID)).thenReturn(mockResponse);

        //when
        Response response = pollService.getPoll(POLL_ID);

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(Map.class);
        Map<String, Object> result = (Map<String, Object>) response.getEntity();
        assertThat(result).containsEntry("pollId", POLL_ID);
        assertThat(result).containsEntry("question", QUESTION);
        assertThat(result).containsKey("options");

        verify(pollRepository).getPollByPollId(POLL_ID);  // Ensure the repository method was called
    }

    @Test
    public void testGetPoll_pollNotFound() {
        //given
        GetItemResponse mockResponse = GetItemResponse.builder().item(null).build();
        when(pollRepository.getPollByPollId(POLL_ID)).thenReturn(mockResponse);

        //when
        Response response = pollService.getPoll(INVALID_OPTION_ID);

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Poll not found");
        verify(pollRepository).getPollByPollId("invalid");  // Ensure the repository method was called
    }

    @Test
    public void testGetPoll_missingPollId() {
        //when
        Response response = pollService.getPoll("");

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Poll ID is required");
    }

    @Test
    public void testVote_success() {
        //given
        when(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).thenReturn(true);
        Map<String, String> requestBody = Map.of("optionId", OPTION_ID, "pollId", POLL_ID);
        when(pollRepository.getOptionsByPollId(POLL_ID)).thenReturn(QueryResponse.builder().build());

        //when
        Response response = pollService.vote(requestBody);

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

        verify(pollRepository).incrementVoteCount(POLL_ID, OPTION_ID);  // Ensure the repository method was called
    }

    @Test
    public void testVote_missingOptionId() {
        //given
        Map<String, String> requestBody = new HashMap<>();

        //when
        Response response = pollService.vote(requestBody);

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Poll ID is required");
    }

    @Test
    public void testVote_failure() {
        //given
        when(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).thenThrow(DynamoDbException.class);
        Map<String, String> requestBody = Map.of("optionId", OPTION_ID, "pollId", POLL_ID);

        //when
        Response response = pollService.vote(requestBody);

        //then
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Failed to update the vote count. Please try again");
    }
}