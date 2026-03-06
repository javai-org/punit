package org.javai.punit.ptest.bernoulli;

import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.reporting.RateFormat;

/**
 * Formats human-readable verdict messages for probabilistic test results.
 *
 * <p>This class handles the presentation of pass/fail verdicts, including
 * execution details, termination reasons, and example failure messages.
 * Extracted from {@link FinalVerdictDecider} to separate formatting from
 * the actual pass/fail decision logic.
 *
 * @see FinalVerdictDecider
 */
class VerdictMessageFormatter {

    /**
     * Builds a statistically qualified failure message.
     *
     * @param aggregator the aggregated sample results
     * @param context the statistical context with baseline and confidence data
     * @return a formatted failure message with statistical qualifications
     */
    String buildFailureMessage(SampleResultAggregator aggregator,
                               BernoulliFailureMessages.StatisticalContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.isSpecDriven()) {
            sb.append(BernoulliFailureMessages.probabilisticTestFailure(context));
        } else {
            sb.append(BernoulliFailureMessages.probabilisticTestFailureInlineThreshold(
                    context.observedRate(),
                    context.successes(),
                    context.samples(),
                    context.threshold()));
        }
        sb.append("\n\n");

        appendExecutionDetails(sb, aggregator);

        return sb.toString();
    }

    /**
     * Builds a summary message for a passing test.
     *
     * @param aggregator the aggregated sample results
     * @param minPassRate the minimum required pass rate
     * @return a formatted summary message
     */
    String buildSuccessMessage(SampleResultAggregator aggregator, double minPassRate) {
        return String.format(
                "Probabilistic test passed: %s >= %s (%d/%d samples succeeded)",
                RateFormat.format(aggregator.getObservedPassRate()),
                RateFormat.format(minPassRate),
                aggregator.getSuccesses(),
                aggregator.getSamplesExecuted());
    }

    private void appendExecutionDetails(StringBuilder sb, SampleResultAggregator aggregator) {
        sb.append(PUnitReporter.labelValueLn("Samples executed:",
                String.format("%d of %d", aggregator.getSamplesExecuted(), aggregator.getTotalSamples())));
        sb.append(PUnitReporter.labelValueLn("Successes:", String.valueOf(aggregator.getSuccesses())));
        sb.append(PUnitReporter.labelValueLn("Failures:", String.valueOf(aggregator.getFailures())));

        aggregator.getTerminationReason().ifPresent(
                reason -> {
                    sb.append(PUnitReporter.labelValueLn("Termination:", reason.name()));
                    String details = aggregator.getTerminationDetails();
                    if (details != null && !details.isEmpty()) {
                        sb.append(PUnitReporter.labelValueLn("Reason:", details));
                    }
                }
        );

        sb.append(PUnitReporter.labelValueLn("Elapsed:", aggregator.getElapsedMs() + "ms"));

        if (!aggregator.getExampleFailures().isEmpty()) {
            sb.append(String.format("%nExample failures (showing %d of %d):%n",
                    aggregator.getExampleFailures().size(), aggregator.getFailures()));

            int sampleIndex = 1;
            for (Throwable failure : aggregator.getExampleFailures()) {
                String message = failure.getMessage();
                if (message == null || message.isEmpty()) {
                    message = failure.getClass().getSimpleName();
                }
                if (message.length() > 80) {
                    message = message.substring(0, 77) + "...";
                }
                sb.append(String.format("  [Sample %d] %s%n", sampleIndex++, message));
            }
        }
    }
}
