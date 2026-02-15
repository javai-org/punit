package org.javai.punit.experiment.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.ResultProjection;

/**
 * Builds result projections from {@link UseCaseOutcome} instances.
 *
 * <p>Each projection captures the key observable aspects of a single sample:
 * postcondition results, the full content of the typed result, and optional
 * failure detail for error cases.
 *
 * <p>Content is emitted at its natural length — no truncation or padding is applied.
 * Diff alignment across configurations is handled by anchor comment lines in the
 * YAML output, not by fixed-width normalization.
 *
 * @see ResultProjection
 */
public class ResultProjectionBuilder {

    /**
     * Creates a new builder.
     */
    public ResultProjectionBuilder() {}

    /**
     * Builds a projection from a use case outcome.
     *
     * @param sampleIndex the sample index (0-based)
     * @param outcome the use case outcome
     * @return the projection
     */
    public ResultProjection build(int sampleIndex, UseCaseOutcome<?> outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");

        String input = extractInput(outcome.metadata());
        Map<String, String> postconditions = extractPostconditions(outcome);
        String content = extractContent(outcome);

        return new ResultProjection(
            sampleIndex,
            input,
            postconditions,
            outcome.executionTime().toMillis(),
            content,
            null
        );
    }

    /**
     * Builds a projection for an error case.
     *
     * @param sampleIndex the sample index (0-based)
     * @param input the input that was being processed when the error occurred (may be null)
     * @param executionTimeMs execution time in milliseconds
     * @param error the error that occurred
     * @return the projection
     */
    public ResultProjection buildError(int sampleIndex, String input, long executionTimeMs, Throwable error) {
        Objects.requireNonNull(error, "error must not be null");

        String failureDetail = error.getClass().getSimpleName() + ": " + firstLine(error.getMessage());
        Map<String, String> postconditions = Map.of("Execution completed", ResultProjection.FAILED);

        return new ResultProjection(
            sampleIndex,
            input,
            postconditions,
            executionTimeMs,
            null,
            failureDetail
        );
    }

    private String extractContent(UseCaseOutcome<?> outcome) {
        Object result = outcome.result();
        if (result == null) {
            return null;
        }
        return result.toString();
    }

    /**
     * Extracts postcondition results as a map of description to status.
     *
     * @param outcome the use case outcome
     * @return ordered map of postcondition descriptions to their status
     */
    private Map<String, String> extractPostconditions(UseCaseOutcome<?> outcome) {
        List<PostconditionResult> results = outcome.evaluatePostconditions();
        Map<String, String> postconditions = new LinkedHashMap<>();

        for (PostconditionResult result : results) {
            String status = result.passed() ? ResultProjection.PASSED : ResultProjection.FAILED;
            postconditions.put(result.description(), status);
        }

        return postconditions;
    }

    /**
     * Extracts the input representation from outcome metadata.
     *
     * @param metadata the outcome metadata
     * @return the input string, or null if not found
     */
    private String extractInput(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        List<String> inputKeys = List.of("input", "instruction", "query", "request", "prompt");

        for (String key : inputKeys) {
            Object value = metadata.get(key);
            if (value != null) {
                return value.toString();
            }
        }

        return null;
    }

    private String firstLine(String text) {
        if (text == null) {
            return "null";
        }
        int newline = text.indexOf('\n');
        return newline > 0 ? text.substring(0, newline) : text;
    }
}
