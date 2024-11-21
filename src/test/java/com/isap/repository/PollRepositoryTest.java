package com.isap.repository;

import com.isap.domain.Poll;
import com.isap.exception.NotFoundException;
import com.isap.utils.DynamoDbHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class PollRepositoryTest {

    @Mock
    private DynamoDbHelper dynamoDbHelper;

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
    public void testGetPollByPollId_success() {
        //given
        Map<String, AttributeValue> mockItem = createPollItem();
        GetItemResponse mockResponse = GetItemResponse.builder()
                .item(mockItem)
                .build();
        when(dynamoDbHelper.getItem(any())).thenReturn(mockResponse);

        //when
        GetItemResponse response = pollRepository.getPollByPollId(POLL_ID);

        //then
        assertThat(response).isNotNull();
        assertThat(POLL_ID).isEqualTo(response.item().get("PK").s());
        assertThat(QUESTION).isEqualTo(response.item().get("question").s());
        assertThat(response.item().get("options")).isNotNull();
        assertThat(response.item().get("options").m()).containsKey(OPTION_ID);
        assertThat(response.item().get("options").m().get(OPTION_ID).s()).isEqualTo("Java");
        assertThat(response.item().get("options").m()).containsKey(OPTION_ID2);
        assertThat(response.item().get("options").m().get(OPTION_ID2).s()).isEqualTo("Python");

        verify(dynamoDbHelper).getItem(any());
    }

    // Failure Path - Poll not found
    @Test
    public void testGetPollByPollId_pollIdNotFound() {
        //given
        GetItemResponse mockResponse = GetItemResponse.builder().build();
        when(dynamoDbHelper.getItem(any())).thenReturn(mockResponse);

        //when
        try {
            pollRepository.getPollByPollId(POLL_ID);
            fail("Expected exception but none was thrown");
        } catch (NotFoundException e) {
            assertThat(e).isInstanceOf(NotFoundException.class);
            assertThat(e.getMessage()).contains("Poll not found with ID: " + POLL_ID);
        }

        //then
        verify(dynamoDbHelper).getItem(any());
    }

    // Happy Path - Successfully increment vote count
    @Test
    public void testIncrementVoteCount_success() {
        UpdateItemResponse mockUpdateResponse = mock(UpdateItemResponse.class);
        PutItemResponse mockPutResponse = mock(PutItemResponse.class);
        SdkHttpResponse mockHttpResponse = SdkHttpResponse.builder().statusCode(200).build();

        //given
        when(dynamoDbHelper.updateItem(any(), any(), any())).thenReturn(mockUpdateResponse);
        when(mockUpdateResponse.sdkHttpResponse()).thenReturn(mockHttpResponse);

        when(dynamoDbHelper.putItem(any())).thenReturn(mockPutResponse);
        when(mockPutResponse.sdkHttpResponse()).thenReturn(mockHttpResponse);

        //when then
        assertThat(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).isTrue();
        verify(dynamoDbHelper).updateItem(any(), any(), any());
    }

    // Failure Path - Vote update fails
    @Test
    public void testIncrementVoteCount_failure() {
        //given
        when(dynamoDbHelper.updateItem(any(), any(), any())).thenThrow(DynamoDbException.class);

        //when then
        try {
            assertThat(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).isFalse();
            fail("Expected exception but none was thrown");
        } catch (DynamoDbException e) {
            assertThat(e).isInstanceOf(DynamoDbException.class);
        }

        verify(dynamoDbHelper).updateItem(any(), any(), any());
    }

    // Failure Path - Put vote fails
    @Test
    public void testIncrementVoteCount_putVoteFailure() {
        UpdateItemResponse mockUpdateResponse = mock(UpdateItemResponse.class);
        SdkHttpResponse mockHttpResponse = SdkHttpResponse.builder().statusCode(200).build();

        //given
        when(dynamoDbHelper.updateItem(any(), any(), any())).thenReturn(mockUpdateResponse);
        when(mockUpdateResponse.sdkHttpResponse()).thenReturn(mockHttpResponse);
        when(dynamoDbHelper.putItem(any())).thenThrow(DynamoDbException.class);

        //when then
        try {
            assertThat(pollRepository.incrementVoteCount(POLL_ID, OPTION_ID)).isFalse();
            fail("Expected exception but none was thrown");
        } catch (DynamoDbException e) {
            assertThat(e).isInstanceOf(DynamoDbException.class);
        }
        verify(dynamoDbHelper).updateItem(any(), any(), any());
    }

    // Happy Path: Successfully create a poll
    @Test
    public void testCreatePoll_success() {
        Map<String, List<String>> newPollData = Map.of(QUESTION, List.of("Java", "Python"));

        //given
        when(dynamoDbHelper.createPollAndOptions(any())).thenReturn(true);

        //when
        boolean result = pollRepository.createPoll(newPollData);

        //then
        assertThat(result).isTrue();
        verify(dynamoDbHelper).createPollAndOptions(newPollData);
    }

    // Failure Path: Poll creation fails
    @Test
    public void testCreatePoll_failure() {
        Map<String, List<String>> newPollData = Map.of(QUESTION, List.of("Java", "Python"));

        //given
        when(dynamoDbHelper.createPollAndOptions(any())).thenReturn(false);

        //when
        boolean result = pollRepository.createPoll(newPollData);

        //then
        assertThat(result).isFalse();
        verify(dynamoDbHelper).createPollAndOptions(newPollData);
    }
}
