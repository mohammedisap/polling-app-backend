package com.isap.repository;

import com.isap.domain.Poll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class PollRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private PollRepositoryImpl pollRepository;

    private static final String POLL_ID = "poll1";
    private static final String QUESTION = "What is your favorite programming language?";
    private static final String OPTION_ID = "op1";
    private static final String OPTION_ID2 = "op2";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);  // Initialize mocks
    }

    private Map<String, AttributeValue> createPollItem() {
        Map<String, String> options = Map.of(
                OPTION_ID, "Java",
                OPTION_ID2, "Python"
        );

        Poll poll = new Poll(POLL_ID, QUESTION, options);

        return poll.toDynamoDbItem();
    }

    // Happy Path: Successfully retrieve a poll
    @Test
    public void testGetPollByPoll_Id_success() {
        //given
        Map<String, AttributeValue> mockItem = createPollItem();
        GetItemResponse mockResponse = GetItemResponse.builder()
                .item(mockItem)
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(mockResponse);

        //when
        GetItemResponse response = pollRepository.getPollByPollId(POLL_ID);

        //then
        assertThat(response).isNotNull();
        assertThat(POLL_ID).isEqualTo(response.item().get("PK").s());
        assertThat("What is your favorite programming language?").isEqualTo(response.item().get("question").s());
        assertThat(response.item().get("options")).isNotNull();
        assertThat(response.item().get("options").m()).containsKey(OPTION_ID);
        assertThat(response.item().get("options").m().get(OPTION_ID).s()).isEqualTo("Java");
        assertThat(response.item().get("options").m()).containsKey(OPTION_ID2);
        assertThat(response.item().get("options").m().get(OPTION_ID2).s()).isEqualTo("Python");

        verify(dynamoDbClient).getItem(any(GetItemRequest.class));
    }

    // Failure Path - Poll not found
    @Test
    public void testGetPoll_PollBy_pollIdNotFound() {
        //given
        GetItemResponse mockResponse = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(mockResponse);

        //when
        GetItemResponse response = pollRepository.getPollByPollId(POLL_ID);

        //then
        assertThat(response.item()).isEmpty();
        verify(dynamoDbClient).getItem(any(GetItemRequest.class));
    }

    // Happy Path - Successfully increment vote count
    @Test
    public void testIncrementVoteCount_success() {
        UpdateItemResponse mockUpdateResponse = mock(UpdateItemResponse.class);
        PutItemResponse mockPutResponse = mock(PutItemResponse.class);
        SdkHttpResponse mockHttpResponse = SdkHttpResponse.builder().statusCode(200).build();

        //given
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(mockUpdateResponse);
        when(mockUpdateResponse.sdkHttpResponse()).thenReturn(mockHttpResponse);

        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(mockPutResponse);
        when(mockPutResponse.sdkHttpResponse()).thenReturn(mockHttpResponse);

        //when then
        assertThat(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).isTrue();
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    // Failure Path - Vote update fails
    @Test
    public void testIncrementVoteCount_failure() {
        // Simulate an exception when calling updateItem
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(DynamoDbException.class);

        // Call the method under test
        try {
            assertThat(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).isFalse();
            fail("Expected exception but none was thrown");
        } catch (DynamoDbException e) {
            assertThat(e).isInstanceOf(DynamoDbException.class);
        }

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }


}
