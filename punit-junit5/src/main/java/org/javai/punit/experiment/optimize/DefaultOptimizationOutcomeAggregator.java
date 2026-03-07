package org.javai.punit.experiment.optimize;

import java.util.List;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.contract.match.VerificationMatcher.MatchResult;

/**
 * Default implementation of OptimizationOutcomeAggregator.
 *
 * <p>Computes standard statistics from a list of use case outcomes, including
 * detailed failure feedback for the mutator.
 *
 * <p>Token counts are extracted from the outcome's metadata using common keys
 * ("tokensUsed", "tokens", "totalTokens").
 *
 * <p>Input information is extracted from metadata using common keys
 * ("instruction", "input", "query").
 */
public final class DefaultOptimizationOutcomeAggregator implements OptimizationOutcomeAggregator {

    private static final String[] INPUT_METADATA_KEYS = {"instruction", "input", "query", "request"};

    @Override
    public OptimizeStatistics aggregate(List<UseCaseOutcome<?>> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return OptimizeStatistics.empty();
        }

        int sampleCount = outcomes.size();
        int successCount = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;

        IterationFeedback.Builder feedbackBuilder = new IterationFeedback.Builder();

        for (UseCaseOutcome<?> outcome : outcomes) {
            boolean allPassed = outcome.allPostconditionsSatisfied();
            boolean matchesPassed = outcome.matchesExpected();

            if (allPassed && matchesPassed) {
                successCount++;
            } else {
                // Extract input for context
                String input = extractInput(outcome);

                // Record postcondition failures
                if (!allPassed) {
                    for (PostconditionResult result : outcome.evaluatePostconditions()) {
                        if (result.failed()) {
                            String message = result.failureReason().orElse(null);
                            feedbackBuilder.recordPostconditionFailure(result.description(), message);
                        }
                    }
                    feedbackBuilder.recordFailedInput(input);
                }

                // Record instance conformance mismatches
                if (!matchesPassed && outcome.hasExpectedValue()) {
                    MatchResult matchResult = outcome.getMatchResult().orElse(null);
                    if (matchResult != null && matchResult.mismatches()) {
                        String expected = stringifyExpected(outcome.expectedValue());
                        String actual = stringifyActual(outcome.result());
                        feedbackBuilder.recordInstanceMismatch(input, expected, actual, matchResult.diff());
                    }
                }
            }

            // Extract tokens from metadata using common keys
            totalTokens += outcome.getMetadataLong("tokensUsed", "tokens", "totalTokens").orElse(0L);
            // Get latency from execution time
            totalLatencyMs += outcome.executionTime().toMillis();
        }

        double meanLatencyMs = sampleCount > 0 ? (double) totalLatencyMs / sampleCount : 0.0;
        IterationFeedback feedback = feedbackBuilder.build();

        return OptimizeStatistics.fromCounts(sampleCount, successCount, totalTokens, meanLatencyMs, feedback);
    }

    /**
     * Extracts input from outcome metadata using common keys.
     */
    private String extractInput(UseCaseOutcome<?> outcome) {
        for (String key : INPUT_METADATA_KEYS) {
            Object value = outcome.metadata().get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Converts expected value to string for feedback.
     */
    private String stringifyExpected(Object expected) {
        if (expected == null) return "null";
        return expected.toString();
    }

    /**
     * Converts actual result to string for feedback.
     */
    private String stringifyActual(Object result) {
        if (result == null) return "null";
        // Handle ChatResponse or similar wrapper types
        try {
            var contentMethod = result.getClass().getMethod("content");
            Object content = contentMethod.invoke(result);
            return content != null ? content.toString() : "null";
        } catch (Exception e) {
            return result.toString();
        }
    }
}
