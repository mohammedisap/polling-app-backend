package com.isap.service;

import com.isap.repository.PollRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.*;

public class PollServiceApiTest {

    @Mock
    private PollRepository pollRepository;

    @InjectMocks
    private PollServiceImpl pollService;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost:8080"; // Adjust this to your API base URI
    }

    // Test case for fetching a poll
    @Test
    public void testGetPoll_Success() {
        when(pollRepository.getPollByPollId("poll1")).thenReturn(mockGetPollResponse());

        given()
                .queryParam("pollId", "poll1")
                .when()
                .get("/poll")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("pollId", equalTo("poll1"))
                .body("question", equalTo("What is your favorite programming language?"))
                .body("options.size()", greaterThan(0));

        verify(pollRepository, times(1)).getPollByPollId("poll1");
    }

    // Test case for invalid pollId (not found)
    @Test
    public void testGetPoll_NotFound() {
        when(pollRepository.getPollByPollId("invalid_poll")).thenReturn(null);

        given()
                .queryParam("pollId", "invalid_poll")
                .when()
                .get("/poll")
                .then()
                .statusCode(404)
                .body("message", equalTo("Poll not found"));

        verify(pollRepository, times(1)).getPollByPollId("invalid_poll");
    }

    // Test case for creating a poll
    @Test
    public void testCreatePoll_Success() {
        Map<String, Object> newPollData = new HashMap<>();
        newPollData.put("question", "What is your favorite color?");
        newPollData.put("options", List.of("Red", "Blue", "Green"));

        when(pollRepository.createPoll(any())).thenReturn(true);

        given()
                .contentType(ContentType.JSON)
                .body(newPollData)
                .when()
                .post("/poll/create")
                .then()
                .statusCode(201)
                .body("message", equalTo("Poll created successfully"));

        verify(pollRepository, times(1)).createPoll(any());
    }

    @Test
    public void testCreatePoll_Failure() {
        Map<String, Object> newPollData = new HashMap<>();
        newPollData.put("question", "What is your favorite color?");
        // Missing options (should fail)

        when(pollRepository.createPoll(any())).thenThrow(DynamoDbException.builder()
                .message("Error creating poll")
                .cause(new Throwable("Cause of the error"))
                .build());

        given()
                .contentType(ContentType.JSON)
                .body(newPollData)
                .when()
                .post("/poll/create")
                .then()
                .statusCode(400)
                .body("message", equalTo("Error creating poll"));

        verify(pollRepository, times(1)).createPoll(any());
    }

    private GetItemResponse mockGetPollResponse() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s("poll1").build());
        item.put("question", AttributeValue.builder().s("What is your favorite programming language?").build());

        Map<String, AttributeValue> optionsMap = new HashMap<>();
        optionsMap.put("op1", AttributeValue.builder().s("Java").build());
        optionsMap.put("op2", AttributeValue.builder().s("Python").build());
        item.put("options", AttributeValue.builder().m(optionsMap).build());

        return GetItemResponse.builder()
                .item(item)
                .build();
    }

    @Test
    public void testPollOptionsAssertion() {
        GetItemResponse response = mockGetPollResponse();

        Map<String, AttributeValue> item = response.item();
        String pollId = item.get("PK").s();
        String question = item.get("question").s();
        Map<String, AttributeValue> options = item.get("options").m();

        assertThat(pollId).isEqualTo("poll1");
        assertThat(question).isEqualTo("What is your favorite programming language?");
        assertThat(options).containsKey("op1");
        assertThat(options).containsKey("op2");
        assertThat(options.get("op1").s()).isEqualTo("Java");
        assertThat(options.get("op2").s()).isEqualTo("Python");
    }
}