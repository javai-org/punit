package org.javai.punit.api.match;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.javai.punit.api.MatchResult;
import org.javai.punit.api.ValueMatcher;

/**
 * Semantic JSON comparison matcher.
 *
 * <p>Compares JSON strings as parsed trees, ignoring property
 * ordering and insignificant whitespace. Mismatches produce a
 * human-readable diff describing add / remove / replace operations
 * with JSON-pointer-like paths.
 *
 * <p>Backed by Jackson; punit-core declares Jackson as a hard
 * runtime dependency, so {@link #create()} always succeeds.
 *
 * @see ValueMatcher
 * @see StringMatcher
 */
public final class JsonMatcher implements ValueMatcher<String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DESCRIPTION = "json equals expected (structural)";

    private JsonMatcher() {
        // construct via create()
    }

    /** Creates a JSON matcher. */
    public static JsonMatcher create() {
        return new JsonMatcher();
    }

    @Override
    public MatchResult match(String expected, String actual) {
        if (expected == null && actual == null) {
            return MatchResult.pass(DESCRIPTION, null, null);
        }
        if (expected == null) {
            return MatchResult.fail(DESCRIPTION, null, actual,
                    "expected null but got JSON");
        }
        if (actual == null) {
            return MatchResult.fail(DESCRIPTION, expected, null,
                    "expected JSON but got null");
        }

        JsonNode expectedNode;
        JsonNode actualNode;

        try {
            expectedNode = OBJECT_MAPPER.readTree(expected);
        } catch (JsonProcessingException e) {
            return MatchResult.fail(DESCRIPTION, expected, actual,
                    "expected value is not valid JSON: " + e.getMessage());
        }

        try {
            actualNode = OBJECT_MAPPER.readTree(actual);
        } catch (JsonProcessingException e) {
            return MatchResult.fail(DESCRIPTION, expected, actual,
                    "actual value is not valid JSON: " + e.getMessage());
        }

        if (expectedNode.equals(actualNode)) {
            return MatchResult.pass(DESCRIPTION, expected, actual);
        }

        return MatchResult.fail(DESCRIPTION, expected, actual,
                buildDiff(expectedNode, actualNode));
    }

    // ── Diff construction ────────────────────────────────────────────

    private static String buildDiff(JsonNode expected, JsonNode actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("JSON differences:\n");
        collectDifferences("", expected, actual, sb);
        return sb.toString().stripTrailing();
    }

    private static void collectDifferences(String path, JsonNode expected, JsonNode actual, StringBuilder sb) {
        if (expected.equals(actual)) {
            return;
        }

        if (expected.isObject() && actual.isObject()) {
            collectObjectDifferences(path, (ObjectNode) expected, (ObjectNode) actual, sb);
        } else if (expected.isArray() && actual.isArray()) {
            collectArrayDifferences(path, (ArrayNode) expected, (ArrayNode) actual, sb);
        } else {
            sb.append("  - replace at '").append(path).append("': ")
                    .append(truncateValue(expected.toString()))
                    .append(" → ")
                    .append(truncateValue(actual.toString()))
                    .append("\n");
        }
    }

    private static void collectObjectDifferences(String path, ObjectNode expected, ObjectNode actual, StringBuilder sb) {
        Set<String> allFields = new LinkedHashSet<>();
        expected.fieldNames().forEachRemaining(allFields::add);
        actual.fieldNames().forEachRemaining(allFields::add);

        for (String field : allFields) {
            String fieldPath = path.isEmpty() ? "/" + field : path + "/" + field;

            if (!expected.has(field)) {
                sb.append("  - add at '").append(fieldPath).append("': ")
                        .append(truncateValue(actual.get(field).toString()))
                        .append("\n");
            } else if (!actual.has(field)) {
                sb.append("  - remove at '").append(fieldPath).append("'\n");
            } else {
                collectDifferences(fieldPath, expected.get(field), actual.get(field), sb);
            }
        }
    }

    private static void collectArrayDifferences(String path, ArrayNode expected, ArrayNode actual, StringBuilder sb) {
        int maxLen = Math.max(expected.size(), actual.size());
        for (int i = 0; i < maxLen; i++) {
            String elementPath = path + "/" + i;
            if (i >= expected.size()) {
                sb.append("  - add at '").append(elementPath).append("': ")
                        .append(truncateValue(actual.get(i).toString()))
                        .append("\n");
            } else if (i >= actual.size()) {
                sb.append("  - remove at '").append(elementPath).append("'\n");
            } else {
                collectDifferences(elementPath, expected.get(i), actual.get(i), sb);
            }
        }
    }

    private static String truncateValue(String value) {
        if (value.length() <= 50) {
            return value;
        }
        return value.substring(0, 47) + "...";
    }
}
