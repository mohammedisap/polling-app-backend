The backend is assumed to be a single lambda that is triggered via an API
I chose quarkus over spring boot for its requirement for lower memory, faster startup times - very important in a 
lambda environment, and it's support for GraalVM allows us to create a very lightweight _native_ executable.  

Assumptions

DynamoDB is used for data storage, with tables designed for scalability and low-latency reads/writes. Primary keys are structured as follows:

`{
    PK: "5f7241ae-bb30-4e68-92e9-c7028547f93d"
    SK: "poll",
    question: "What is your favourite programming language?"
    options: {
        "5f7241ae-bb30-4e68-92e9-c7028547f93d": "Java",
        "ec6e3d54-1280-4150-8465-6d63f032c7cb": "Python",
        "5c3e9375-f39c-4cd3-b7bf-3544f70fbf0c": "Scala",
        "cad541a6-518a-41c6-91fa-39c501cc7d92": "Go"
    }
}`

`{
    PK: "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    SK: "option",
    GSI1PK: "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    GSI1SK: "poll",
    text: "Java",
    votes: 120
}
`

`{
    PK: "42ab0c23-e5bd-4a21-93a8-85bb376dcb30",
    SK: "vote",
    GSI1PK: "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    GSI1SK: "poll",
    GSI2PK: "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    GSI2SK: "option",
    "pollId": "5f7241ae-bb30-4e68-92e9-c7028547f93d",
    "optionId": "46a55f25-218a-44a6-b9dd-eb5b4f644ec6",
    "timestamp": "2024-12-20T12:34:56Z"
}`

**AWS Lambda Function**

Deployed for handling the business logic (e.g., voting, retrieving poll data).
I envisage this polling function to be a small component of a larger page, that MAY NOT be interacted be so often. 

Keeping the backend small and lightweight is beneficial here, while AWS Lambda may experience frequent cold starts, careful attention was given to function optimization, where I used Quarkus for a lightweight and fast startup.
Quarkus was chosen over Spring Boot for Lambda deployment due to its lower memory footprint, faster startup times, and native support for GraalVM, which allows for the creation of an optimized, lightweight native executable. 
These characteristics are particularly beneficial in serverless environments like AWS Lambda, where cold start latency and resource efficiency are crucial. Additionally, Quarkus offers a smaller binary size, which reduces deployment size and improves performance compared to Spring Boot.

AWS Lambda also allows for the other extreme to be handled with ease as it would scale automatically based on demand.


**API Gateway** 

Used to expose Lambda functions as HTTP endpoints for client interaction. The API routes are designed to handle HTTP methods like GET for fetching poll data and POST for submitting votes.


How It Works

API Gateway routes HTTP requests to the appropriate Lambda functions.
Lambda handles business logic such as submitting votes and retrieving poll data from DynamoDB.
DynamoDB stores poll data and vote counts.


**Poll Service API Documentation**
1. Create a New Poll
    Endpoint: POST /poll/create
    Description: Creates a new poll with a question and options.
    
    Request Body:
    {
        "question": "What is your favorite color?",
        "options": ["Red", "Blue", "Green"]
    }
    
    Response:
    201 Created: Poll created successfully.
    400 Bad Request: Invalid request body (missing question or options).
    500 Internal Server Error: Error creating the poll.

2. Get Poll by ID
    Endpoint: GET /poll
    Description: Fetches the details of a poll by pollId.
    Query Parameter: pollId (Required)
    Response:
    200 OK: Returns poll details with options.
    400 Bad Request: Missing or invalid poll ID.
    404 Not Found: Poll not found.

3. Vote on a Poll
    Endpoint: POST /poll/vote
    Description: Allows a user to vote for an option in a poll.
    
    Request Body:
    {
        "pollId": "12345",
        "optionId": "67890"
    }

    Response:
    200 OK: Vote counted successfully.
    400 Bad Request: Missing pollId or optionId.
    500 Internal Server Error: Error while updating the vote.

4. Get Poll Votes
    Endpoint: GET /poll/votes
    Description: Retrieves the voting stats (optionId and timestamp) for a poll.
    Query Parameter: pollId (Required)
   
    Response:
    200 OK: Returns a list of options and their timestamps.
    400 Bad Request: Missing or invalid poll ID.
    
**Example Responses**
   
Poll Creation Response:
`{
    "status": "Poll created successfully"
}`
   
Get Poll Response:
`{
    "pollId": "12345",
    "question": "What is your favorite color?",
    "options": ["Red", "Blue", "Green"]
}`
   
Vote Response:
`{
    "status": "Vote counted successfully"
}`
   
Get Poll Votes:
`[
    {
         "voteId": "09653015-a6c9-49c3-93d1-800555943eb9",
         "optionId": "cad541a6-518a-41c6-91fa-39c501cc7d92"
         "timestamp": "2024-11-20T12:34:56Z"
    },
    {
        "voteId": "22145313-ae87-4ecb-87e4-bd0a6cfb3468",
        "optionId": "ec6e3d54-1280-4150-8465-6d63f032c7cb"
        "timestamp": "2024-12-20T12:34:56Z"
    }
]`


To build the project run `gradle build`
To test `gradle test`