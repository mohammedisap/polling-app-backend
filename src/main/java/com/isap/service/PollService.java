package com.isap.service;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public interface PollService {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getPoll(@QueryParam("pollId") String pollId);

    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_JSON)
    Response vote(Map<String, String> requestBody);

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createPoll(Map<String, Object> newPollData);

    @GET
    @Path("/votes")
    @Produces(MediaType.APPLICATION_JSON)
    Response getPollVotes(@QueryParam("pollId") String pollId);
}
