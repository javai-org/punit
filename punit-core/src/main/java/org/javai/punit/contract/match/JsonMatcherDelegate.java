package org.javai.punit.contract.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;

/**
 * Internal delegate that performs JSON comparison using zjsonpatch.
 *
 * <p>This class is package-private and isolated to ensure that zjsonpatch
 * class loading only occurs when actually needed. This enables graceful
 * handling of the optional dependency.
 */
final class JsonMatcherDelegate {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonMatcherDelegate() {
        // Utility class
    }

    /**
     * Compares two JSON strings semantically.
     *
     * @param expected the expected JSON string
     * @param actual the actual JSON string
     * @return the match result
     */
    static VerificationMatcher.MatchResult compare(String expected, String actual) {
        JsonNode expectedNode;
        JsonNode actualNode;

        try {
            expectedNode = OBJECT_MAPPER.readTree(expected);
        } catch (JsonProcessingException e) {
            return VerificationMatcher.MatchResult.mismatch(
                    "expected value is not valid JSON: " + e.getMessage()
            );
        }

        try {
            actualNode = OBJECT_MAPPER.readTree(actual);
        } catch (JsonProcessingException e) {
            return VerificationMatcher.MatchResult.mismatch(
                    "actual value is not valid JSON: " + e.getMessage()
            );
        }

        // Use zjsonpatch to compute the diff
        JsonNode diff = JsonDiff.asJson(expectedNode, actualNode);

        if (diff.isEmpty()) {
            return VerificationMatcher.MatchResult.match();
        }

        // Format the diff as a readable string
        return VerificationMatcher.MatchResult.mismatch(formatDiff(diff));
    }

    private static String formatDiff(JsonNode diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("JSON differences (RFC 6902):\n");

        for (JsonNode operation : diff) {
            String op = operation.get("op").asText();
            String path = operation.get("path").asText();

            sb.append("  - ").append(op).append(" at '").append(path).append("'");

            if (operation.has("value")) {
                sb.append(": ").append(truncateValue(operation.get("value").toString()));
            }
            if (operation.has("from")) {
                sb.append(" from '").append(operation.get("from").asText()).append("'");
            }

            sb.append("\n");
        }

        return sb.toString().stripTrailing();
    }

    private static String truncateValue(String value) {
        if (value.length() <= 50) {
            return value;
        }
        return value.substring(0, 47) + "...";
    }
}
