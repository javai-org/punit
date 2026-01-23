package org.javai.punit.spec.criteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.contract.PostconditionResult;

/**
 * Aggregates postcondition outcomes across multiple experiment samples.
 *
 * <p>This aggregator tracks:
 * <ul>
 *   <li>Pass/fail rates for each postcondition across all samples</li>
 *   <li>Overall success rate (all postconditions passed)</li>
 *   <li>Error statistics for postconditions that threw exceptions</li>
 * </ul>
 *
 * <p>The aggregator preserves postcondition insertion order for consistent reporting.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * PostconditionAggregator aggregator = new PostconditionAggregator();
 *
 * for (int i = 0; i < samples; i++) {
 *     UseCaseOutcome<?> outcome = contract.execute(input);
 *     aggregator.record(outcome.evaluatePostconditions());
 * }
 *
 * Map<String, PostconditionStats> stats = aggregator.getPostconditionStats();
 * double overallRate = aggregator.getOverallSuccessRate();
 * }</pre>
 *
 * @see PostconditionResult
 */
public class PostconditionAggregator {

    private final Map<String, PostconditionStats> statsByDescription = new LinkedHashMap<>();
    private int samplesRecorded = 0;
    private int allPassedCount = 0;

    /**
     * Records outcomes from a postcondition evaluation.
     *
     * <p>Each postcondition's outcome is recorded in the aggregated statistics.
     *
     * @param results the postcondition results to record
     * @throws NullPointerException if results is null
     */
    public void record(List<PostconditionResult> results) {
        Objects.requireNonNull(results, "results must not be null");

        samplesRecorded++;

        boolean allPassed = true;
        for (PostconditionResult result : results) {
            PostconditionStats stats = statsByDescription.computeIfAbsent(
                result.description(),
                PostconditionStats::new
            );
            stats.record(result);

            if (!result.passed()) {
                allPassed = false;
            }
        }

        if (allPassed) {
            allPassedCount++;
        }
    }

    /**
     * Returns statistics for each postcondition, keyed by description.
     *
     * <p>The map preserves insertion order (first postcondition seen comes first).
     *
     * @return unmodifiable map of postcondition descriptions to statistics
     */
    public Map<String, PostconditionStats> getPostconditionStats() {
        return Collections.unmodifiableMap(statsByDescription);
    }

    /**
     * Returns the overall success rate (samples where all postconditions passed).
     *
     * @return success rate from 0.0 to 1.0
     */
    public double getOverallSuccessRate() {
        if (samplesRecorded == 0) {
            return 0.0;
        }
        return (double) allPassedCount / samplesRecorded;
    }

    /**
     * Returns the number of samples recorded.
     *
     * @return sample count
     */
    public int getSamplesRecorded() {
        return samplesRecorded;
    }

    /**
     * Returns the number of samples where all postconditions passed.
     *
     * @return count of fully successful samples
     */
    public int getAllPassedCount() {
        return allPassedCount;
    }

    /**
     * Returns a summary suitable for inclusion in spec files.
     *
     * <p>The summary is a map of postcondition descriptions to pass rates:
     * <pre>
     * postconditionPassRates:
     *   "JSON parsed": 0.98
     *   "Has products": 0.95
     *   "No hallucination": 0.92
     * </pre>
     *
     * @return map of descriptions to pass rates
     */
    public Map<String, Double> getPassRateSummary() {
        Map<String, Double> summary = new LinkedHashMap<>();
        for (Map.Entry<String, PostconditionStats> entry : statsByDescription.entrySet()) {
            summary.put(entry.getKey(), entry.getValue().getPassRate());
        }
        return summary;
    }

    /**
     * Statistics for a single postcondition across all samples.
     */
    public static class PostconditionStats {
        private final String description;
        private int passed = 0;
        private int failed = 0;
        private int skipped = 0;
        private final List<String> failureMessages = new ArrayList<>();

        PostconditionStats(String description) {
            this.description = description;
        }

        void record(PostconditionResult result) {
            if (result.passed()) {
                passed++;
            } else {
                String reason = result.failureReason().orElse("");
                if (reason.startsWith("Skipped:")) {
                    skipped++;
                } else {
                    failed++;
                    // Keep first few failure messages for diagnostics
                    if (failureMessages.size() < 5 && !reason.isEmpty()) {
                        failureMessages.add(reason);
                    }
                }
            }
        }

        /**
         * Returns the postcondition description.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the count of samples where this postcondition passed.
         */
        public int getPassed() {
            return passed;
        }

        /**
         * Returns the count of samples where this postcondition failed.
         */
        public int getFailed() {
            return failed;
        }

        /**
         * Returns the count of samples where this postcondition was skipped.
         *
         * <p>Skipped postconditions are those that couldn't be evaluated because
         * a prerequisite derivation failed.
         */
        public int getSkipped() {
            return skipped;
        }

        /**
         * Returns the total number of samples this postcondition was recorded for.
         */
        public int getTotal() {
            return passed + failed + skipped;
        }

        /**
         * Returns the pass rate for this postcondition.
         */
        public double getPassRate() {
            int total = getTotal();
            if (total == 0) {
                return 0.0;
            }
            return (double) passed / total;
        }

        /**
         * Returns sample failure messages for diagnostics.
         */
        public List<String> getFailureMessages() {
            return Collections.unmodifiableList(failureMessages);
        }
    }
}
