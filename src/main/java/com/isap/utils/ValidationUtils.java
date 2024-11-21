package com.isap.utils;

import java.util.List;
import java.util.Map;

public class ValidationUtils {

    /**
     * Validates the Poll ID to ensure it's not null or empty.
     * @param pollId The poll ID to validate.
     * @return true if valid, false if invalid.
     */
    public static boolean validatePollId(String pollId) {
        return pollId != null && !pollId.isEmpty();
    }

    /**
     * Validates the Option ID to ensure it's not null or empty.
     * @param optionId The option ID to validate.
     * @return true if valid, false if invalid.
     */
    public static boolean validateOptionId(String optionId) {
        return optionId != null && !optionId.isEmpty();
    }

    /**
     * Validates the number of options for a poll.
     * @param options List of options for the poll.
     * @return true if valid, false if invalid.
     */
    public static boolean validatePollOptionsCount(Map<String, String> options) {
        return options.size() >= 2 && options.size() <= 7;
    }

    /**
     * Validates the entire request for creating a new poll.
     * @param pollData The data for creating a new poll.
     * @return true if valid, false if invalid.
     */
    public static boolean validateCreatePollRequest(Map<String, Object> pollData) {
        if (!pollData.containsKey("question") || !(pollData.get("question") instanceof String)) {
            return false;
        }

        return pollData.containsKey("options") && pollData.get("options") instanceof List;
    }
}
