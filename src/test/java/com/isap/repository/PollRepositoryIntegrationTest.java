package com.isap.repository;

import com.isap.domain.Poll;
import com.isap.utils.DynamoDbHelper;
import com.isap.repository.PollRepositoryImpl;
import com.google.common.truth.Truth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class PollRepositoryIntegrationTest {

    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbHelper dynamoDbHelper;
    private static PollRepositoryImpl pollRepository;
    
    private static final String POLL_ID = "poll1";
    private static final String QUESTION = "What is your favorite programming language?";
    private static final String OPTION_ID = "op1";
    private static final String OPTION_ID2 = "op2";

    @BeforeAll
    public static void setUpOnce() {
        // Set up local DynamoDB instance
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:8000"))
                .region(Region.EU_WEST_1)
                .build();

        dynamoDbHelper = new DynamoDbHelper(dynamoDbClient);
        pollRepository = new PollRepositoryImpl(dynamoDbHelper);

        // Create table for tests
        createPollTable();
    }

    @BeforeEach
    public void setUp() {
        // Prepare the test data
        prepareTestPoll();
    }

    private static void createPollTable() {
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName("PollTable")
                .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI1PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI1SK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI2PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI2SK").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(
                        ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                                .indexName("GSI1")
                                .keySchema(
                                        KeySchemaElement.builder().attributeName("GSI1PK").keyType(KeyType.HASH).build(),
                                        KeySchemaElement.builder().attributeName("GSI1SK").keyType(KeyType.RANGE).build())
                                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                .provisionedThroughput(
                                        ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                                .build(),
                        GlobalSecondaryIndex.builder()
                                .indexName("GSI2")
                                .keySchema(
                                        KeySchemaElement.builder().attributeName("GSI2PK").keyType(KeyType.HASH).build(),
                                        KeySchemaElement.builder().attributeName("GSI2SK").keyType(KeyType.RANGE).build())
                                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                .provisionedThroughput(
                                        ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                                .build()
                )
                .build();

        dynamoDbClient.createTable(request);
    }

    private void prepareTestPoll() {
        // Create a poll item
        Poll poll = new Poll(POLL_ID, QUESTION, Map.of(OPTION_ID, "Java", OPTION_ID2, "Python"));
        Map<String, AttributeValue> pollItem = poll.toDynamoDbItem();

        // Insert the poll item into the table
        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName("PollTable")
                .item(pollItem)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }

    @Test
    public void testGetPollByPollId() {
        // When: Retrieving the poll by its ID
        GetItemResponse response = pollRepository.getPollByPollId(POLL_ID);

        // Then: Verify that the poll is returned correctly
        Truth.assertThat(response).isNotNull();
        Truth.assertThat(response.item().get("PK").s()).isEqualTo(POLL_ID);
        Truth.assertThat(response.item().get("question").s()).isEqualTo(QUESTION);
        Truth.assertThat(response.item().get("options").m().containsKey(OPTION_ID)).isTrue();
    }

    @Test
    public void testIncrementVoteCount() {
        // When: Incrementing vote count for a given option
        boolean result = pollRepository.incrementVoteCount(POLL_ID, OPTION_ID);

        // Then: Verify that the vote count was incremented successfully
        Truth.assertThat(result).isTrue();
    }

    @Test
    public void testCreatePoll() {
        // When: Creating a new poll
        Map<String, List<String>> newPollData = Map.of(
                "What is your favorite color?", List.of("Red", "Blue", "Green")
        );

        boolean result = pollRepository.createPoll(newPollData);

        // Then: Verify that the poll was created successfully
        Truth.assertThat(result).isTrue();
    }

    @Test
    public void testGetVotesByPollId() {
        // When: Retrieving the votes for a poll
        QueryResponse response = pollRepository.getVotesByPollId(POLL_ID);

        // Then: Verify the response
        Truth.assertThat(response).isNotNull();
        Truth.assertThat(response.items()).isNotEmpty();
    }

    @Test
    public void testGetOptionsByPollId() {
        // When: Retrieving the options for a poll
        QueryResponse response = pollRepository.getOptionsByPollId(POLL_ID);

        // Then: Verify the response
        Truth.assertThat(response).isNotNull();
        Truth.assertThat(response.items()).isNotEmpty();
    }
}