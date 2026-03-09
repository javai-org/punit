package org.javai.punit.ptest.engine;

import org.javai.punit.reporting.PUnitReporter;

/**
 * Renders the latency assertion line for the summary verdict output.
 *
 * <p>Produces output like:
 * <pre>
 * Latency (n=199): p95 420ms &lt;= 500ms, p99 810ms &lt;= 1000ms
 * Latency (n=199): p95 420ms &lt;= 500ms (from baseline), p99 1200ms > 1000ms ← BREACH
 * </pre>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class LatencySummaryRenderer {

    /**
     * Renders the latency assertion result as a summary line.
     *
     * @param result the latency assertion result
     * @return the rendered line, or empty string if latency was not evaluated
     */
    String render(LatencyAssertionResult result) {
        if (!result.wasEvaluated()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Latency (n=").append(result.successfulSampleCount()).append("): ");

        var percentiles = result.percentileResults();
        for (int i = 0; i < percentiles.size(); i++) {
            if (i > 0) sb.append(", ");

            var pr = percentiles.get(i);
            sb.append(pr.label()).append(" ")
              .append(pr.observedMs()).append("ms ");

            if (pr.passed()) {
                sb.append("<= ").append(pr.thresholdMs()).append("ms");
            } else {
                sb.append("> ").append(pr.thresholdMs()).append("ms \u2190 BREACH");
                boolean hasExplicitThresholds = result.percentileResults().stream()
                        .anyMatch(p -> "explicit".equals(p.source()));
                if (!LatencyAssertionConfig.isEffectivelyEnforced(hasExplicitThresholds)) {
                    sb.append(" (advisory)");
                }
            }

            // Append source if not just "explicit"
            if (pr.source() != null && !pr.source().equals("explicit")) {
                sb.append(" (").append(pr.source()).append(")");
            }

            if (pr.indicative()) {
                sb.append(" (indicative)");
            }
        }

        return sb.toString();
    }

    /**
     * Appends latency result to a StringBuilder for the console summary.
     *
     * @param sb the string builder to append to
     * @param result the latency assertion result
     */
    void appendTo(StringBuilder sb, LatencyAssertionResult result) {
        if (!result.wasEvaluated()) {
            return;
        }

        String rendered = render(result);
        if (!rendered.isEmpty()) {
            sb.append(PUnitReporter.labelValueLn("Latency:", rendered.substring("Latency ".length())));
        }

        // Append caveats
        for (String caveat : result.caveats()) {
            sb.append(PUnitReporter.labelValueLn("Note:", caveat));
        }
    }
}
