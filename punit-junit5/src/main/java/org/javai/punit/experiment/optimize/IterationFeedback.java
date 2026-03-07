package org.javai.punit.experiment.optimize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Captures detailed feedback from an optimization iteration for the mutator.
 *
 * <p>This record provides the mutator with actionable information about what went wrong,
 * enabling it to make targeted improvements to the control factor (e.g., system prompt).
 *
 * <h2>Feedback Sources</h2>
 * <ul>
 *   <li><b>Postcondition failures</b> - Which checks failed and sample error messages</li>
 *   <li><b>Instance conformance</b> - Mismatches between actual and expected results</li>
 *   <li><b>Failed inputs</b> - The inputs that caused failures (for context)</li>
 * </ul>
 *
 * <h2>Usage by Mutator</h2>
 * <p>The mutator LLM receives this feedback formatted as context, enabling it to:
 * <ul>
 *   <li>Identify which postconditions are failing most often</li>
 *   <li>Understand the nature of failures from error messages</li>
 *   <li>See which inputs are problematic</li>
 *   <li>Make targeted prompt improvements</li>
 * </ul>
 *
 * @param postconditionFailures map of postcondition description to failure details
 * @param instanceMismatches list of instance conformance mismatches (expected vs actual)
 * @param failedInputSamples sample inputs that caused failures (for context)
 *
 * @see PostconditionFailure
 * @see InstanceMismatch
 */
public record IterationFeedback(
        Map<String, PostconditionFailure> postconditionFailures,
        List<InstanceMismatch> instanceMismatches,
        List<String> failedInputSamples
) {
    /**
     * Maximum number of failure messages to retain per postcondition.
     */
    public static final int MAX_MESSAGES_PER_POSTCONDITION = 5;

    /**
     * Maximum number of instance mismatches to retain.
     */
    public static final int MAX_INSTANCE_MISMATCHES = 5;

    /**
     * Maximum number of failed input samples to retain.
     */
    public static final int MAX_FAILED_INPUTS = 5;

    /**
     * Creates an IterationFeedback with defensive copies.
     */
    public IterationFeedback {
        postconditionFailures = postconditionFailures != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(postconditionFailures))
                : Map.of();
        instanceMismatches = instanceMismatches != null
                ? Collections.unmodifiableList(new ArrayList<>(instanceMismatches))
                : List.of();
        failedInputSamples = failedInputSamples != null
                ? Collections.unmodifiableList(new ArrayList<>(failedInputSamples))
                : List.of();
    }

    /**
     * Creates empty feedback (no failures).
     */
    public static IterationFeedback empty() {
        return new IterationFeedback(Map.of(), List.of(), List.of());
    }

    /**
     * Returns true if there are any failures recorded.
     */
    public boolean hasFailures() {
        return !postconditionFailures.isEmpty() || !instanceMismatches.isEmpty();
    }

    /**
     * Returns the total number of postcondition failure occurrences.
     */
    public int totalPostconditionFailures() {
        return postconditionFailures.values().stream()
                .mapToInt(PostconditionFailure::count)
                .sum();
    }

    /**
     * Formats the feedback as a human-readable summary for the mutator LLM.
     *
     * @return formatted feedback string
     */
    public String formatForMutator() {
        if (!hasFailures()) {
            return "No failures recorded.";
        }

        StringBuilder sb = new StringBuilder();

        // Postcondition failures
        if (!postconditionFailures.isEmpty()) {
            sb.append("POSTCONDITION FAILURES:\n");
            for (var entry : postconditionFailures.entrySet()) {
                PostconditionFailure failure = entry.getValue();
                sb.append("  • ").append(entry.getKey())
                        .append(" (").append(failure.count()).append(" failures)\n");
                for (String message : failure.sampleMessages()) {
                    sb.append("    - ").append(truncate(message, 200)).append("\n");
                }
            }
        }

        // Instance conformance mismatches
        if (!instanceMismatches.isEmpty()) {
            sb.append("\nINSTANCE CONFORMANCE FAILURES:\n");
            for (InstanceMismatch mismatch : instanceMismatches) {
                sb.append("  Input: ").append(truncate(mismatch.input(), 100)).append("\n");
                sb.append("  Expected: ").append(truncate(mismatch.expected(), 150)).append("\n");
                sb.append("  Actual: ").append(truncate(mismatch.actual(), 150)).append("\n");
                if (mismatch.diff() != null && !mismatch.diff().isEmpty()) {
                    sb.append("  Diff: ").append(truncate(mismatch.diff(), 200)).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * Details about failures of a specific postcondition.
     *
     * @param description the postcondition description
     * @param count number of times this postcondition failed
     * @param sampleMessages sample failure messages (up to MAX_MESSAGES_PER_POSTCONDITION)
     */
    public record PostconditionFailure(
            String description,
            int count,
            List<String> sampleMessages
    ) {
        public PostconditionFailure {
            Objects.requireNonNull(description);
            sampleMessages = sampleMessages != null
                    ? Collections.unmodifiableList(new ArrayList<>(sampleMessages))
                    : List.of();
        }
    }

    /**
     * Details about an instance conformance mismatch.
     *
     * @param input the input that was processed
     * @param expected the expected result
     * @param actual the actual result
     * @param diff the diff description from the matcher (may be empty)
     */
    public record InstanceMismatch(
            String input,
            String expected,
            String actual,
            String diff
    ) {
        public InstanceMismatch {
            input = input != null ? input : "";
            expected = expected != null ? expected : "";
            actual = actual != null ? actual : "";
            diff = diff != null ? diff : "";
        }
    }

    /**
     * Builder for constructing IterationFeedback incrementally during aggregation.
     */
    public static class Builder {
        private final Map<String, PostconditionFailureBuilder> postconditionBuilders = new LinkedHashMap<>();
        private final List<InstanceMismatch> instanceMismatches = new ArrayList<>();
        private final List<String> failedInputSamples = new ArrayList<>();

        /**
         * Records a postcondition failure.
         *
         * @param description the postcondition description
         * @param message the failure message (may be null)
         */
        public void recordPostconditionFailure(String description, String message) {
            PostconditionFailureBuilder builder = postconditionBuilders.computeIfAbsent(
                    description, PostconditionFailureBuilder::new);
            builder.addFailure(message);
        }

        /**
         * Records an instance conformance mismatch.
         *
         * @param input the input that was processed
         * @param expected the expected result
         * @param actual the actual result
         * @param diff the diff description
         */
        public void recordInstanceMismatch(String input, String expected, String actual, String diff) {
            if (instanceMismatches.size() < MAX_INSTANCE_MISMATCHES) {
                instanceMismatches.add(new InstanceMismatch(input, expected, actual, diff));
            }
        }

        /**
         * Records a failed input sample.
         *
         * @param input the input that caused a failure
         */
        public void recordFailedInput(String input) {
            if (input != null && failedInputSamples.size() < MAX_FAILED_INPUTS) {
                if (!failedInputSamples.contains(input)) {
                    failedInputSamples.add(input);
                }
            }
        }

        /**
         * Builds the IterationFeedback.
         */
        public IterationFeedback build() {
            Map<String, PostconditionFailure> failures = new LinkedHashMap<>();
            for (var entry : postconditionBuilders.entrySet()) {
                failures.put(entry.getKey(), entry.getValue().build());
            }
            return new IterationFeedback(failures, instanceMismatches, failedInputSamples);
        }

        private static class PostconditionFailureBuilder {
            private final String description;
            private int count = 0;
            private final List<String> sampleMessages = new ArrayList<>();

            PostconditionFailureBuilder(String description) {
                this.description = description;
            }

            void addFailure(String message) {
                count++;
                if (message != null && !message.isEmpty() && sampleMessages.size() < MAX_MESSAGES_PER_POSTCONDITION) {
                    if (!sampleMessages.contains(message)) {
                        sampleMessages.add(message);
                    }
                }
            }

            PostconditionFailure build() {
                return new PostconditionFailure(description, count, sampleMessages);
            }
        }
    }
}
