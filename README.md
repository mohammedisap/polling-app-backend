# Polling Service Backend

This service provides a simple polling mechanism, where users can vote on predefined options. The backend is implemented as a single AWS Lambda function triggered via an API Gateway.

## Assumptions

DynamoDB is used for data storage, with tables designed for scalability and low-latency reads/writes. The primary keys are structured as follows:

### Poll
```json
{
  "PK": "5f7241ae-bb30-4e68-92e9-c7028547f93d",
  "SK": "poll",
  "question": "What is your favourite programming language?",
  "options": {
    "5f7241ae-bb30-4e68-92e9-c7028547f93d": "Java",
    "ec6e3d54-1280-4150-8465-6d63f032c7cb": "Python",
    "5c3e9375-f39c-4cd3-b7bf-3544f70fbf0c": "Scala",
    "cad541a6-518a-41c6-91fa-39c501cc7d92": "Go"
  }
}
```

### Option
```json
{
    "PK": "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    "SK": "option",
    "GSI1PK": "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    "GSI1SK": "poll",
    "text": "Java",
    "votes": 120
}
```

### Vote
```json
{
    "PK": "42ab0c23-e5bd-4a21-93a8-85bb376dcb30",
    "SK": "vote",
    "GSI1PK": "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    "GSI1SK": "poll",
    "GSI2PK": "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    "GSI2SK": "option",
    "pollId": "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    "optionId": "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    "timestamp": "2024-12-20T12:34:56Z"
}
```

## AWS Lambda Function

To handle business logic (e.g., voting, retrieving poll data). Quarkus is chosen for a lightweight and fast startup, providing an optimized, native executable via GraalVM. 
This is ideal for environments where cold start latency is a concern. As I envisage this to be a small component on a larger scale page, we don't want to impact user experience with slow feedback.

### **API Gateway**

Used to expose Lambda functions as HTTP endpoints for client interaction. The API routes are designed to handle HTTP methods like GET for fetching poll data and POST for submitting votes.

**How It Works**

1. API Gateway routes HTTP requests to the appropriate Lambda functions.
2. Lambda handles business logic such as submitting votes and retrieving poll data from DynamoDB.
3. DynamoDB stores poll data and vote counts.

## Poll Service API Documentation

### 1. Create a New Poll
- **Endpoint:** `POST /poll/create`
- **Description:** Creates a new poll with a question and options.

  **Request Body:**
    ```json
    {
        "question": "What is your favorite color?",
        "options": ["Red", "Blue", "Green"]
    }
    ```

  **Response:**
    - `201 Created`: Poll created successfully.
    - `400 Bad Request`: Invalid request body (missing question or options).
    - `500 Internal Server Error`: Error creating the poll.

### 2. Get Poll by ID
- **Endpoint:** `GET /poll`
- **Description:** Fetches the details of a poll by pollId.
- **Query Parameter:** `pollId` (Required)

  **Response:**
    - `200 OK`: Returns poll details with options.
    - `400 Bad Request`: Missing or invalid poll ID.
    - `404 Not Found`: Poll not found.

### 3. Vote on a Poll
- **Endpoint:** `POST /poll/vote`
- **Description:** Allows a user to vote for an option in a poll.

  **Request Body:**
    ```json
    {
        "pollId": "12345",
        "optionId": "67890"
    }
    ```

  **Response:**
    - `200 OK`: Vote counted successfully.
    - `400 Bad Request`: Missing pollId or optionId.
    - `500 Internal Server Error`: Error while updating the vote.

### 4. Get Poll Votes
- **Endpoint:** `GET /poll/votes`
- **Description:** Retrieves the voting stats (optionId and timestamp) for a poll.
- **Query Parameter:** `pollId` (Required)

  **Response:**
    - `200 OK`: Returns a list of options and their timestamps.
    - `400 Bad Request`: Missing or invalid poll ID.

## Example Responses

### Poll Creation Response:
```json
{
    "status": "Poll created successfully"
}
```

### Get Poll Response:
```json
{
    "pollId": "12345",
    "question": "What is your favorite color?",
    "options": ["Red", "Blue", "Green"]
}
```

### Vote Response:
```json
{
    "status": "Vote counted successfully"
}
```

### Get Poll Votes:
```json
[
    {
        "voteId": "09653015-a6c9-49c3-93d1-800555943eb9",
        "optionId": "cad541a6-518a-41c6-91fa-39c501cc7d92",
        "timestamp": "2024-11-20T12:34:56Z"
    },
    {
        "voteId": "22145313-ae87-4ecb-87e4-bd0a6cfb3468",
        "optionId": "ec6e3d54-1280-4150-8465-6d63f032c7cb",
        "timestamp": "2024-12-20T12:34:56Z"
    }
]
```

## Building

To build the project, run:

```bash
gradle build
```

To test:

```bash
gradle test
```

Make sure you have an up-to-date version of Quarkus installed on your machine. Instructions can be found here: https://quarkus.io/get-started/.

You will also need a Java 17 runtime installed and active. You can check this by running:

```bash
java -version
```

The build I used was:

```
Java(TM) SE Runtime Environment Oracle GraalVM 17.0.10+11.1 (build 17.0.10+11-LTS-jvmci-23.0-b27)
```

To install the above using `sdkman`, run the following:

```bash
sdk install java 17.0.10-graal
sdk use java 17.0.10-graal
```

I suggest using `sdkman` to manage your installations/versions of Quarkus and Java, it's really a great tool.


### Further Refinement
While I have included the necessary DTOs (Data Transfer Objects) in the project, they have not been fully implemented due to time constraints. 
These DTOs were meant to be used for better separation of concerns and data encapsulation between layers.

In the unit tests, I used any() alot to match arguments passed to mock methods, regardless of their specific values. 
This simplifies the tests by focusing on verifying the behavior of the methods. However this should be changed to use specific values for better testing. 