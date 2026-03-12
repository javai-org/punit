package org.javai.punit.ptest.engine;

import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.PercentileAssertion;

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
     * Renders the latency dimension as a summary line.
     *
     * @param lat the latency dimension from the verdict model
     * @return the rendered line, or empty string if latency was skipped
     */
    String render(LatencyDimension lat) {
        if (lat.skipped()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Latency (n=").append(lat.successfulSamples()).append("): ");

        var assertions = lat.assertions();
        for (int i = 0; i < assertions.size(); i++) {
            if (i > 0) sb.append(", ");

            PercentileAssertion pa = assertions.get(i);
            sb.append(pa.label()).append(" ")
              .append(pa.observedMs()).append("ms ");

            if (pa.passed()) {
                sb.append("<= ").append(pa.thresholdMs()).append("ms");
            } else {
                sb.append("> ").append(pa.thresholdMs()).append("ms \u2190 BREACH");
                boolean hasExplicitThresholds = assertions.stream()
                        .anyMatch(a -> "explicit".equals(a.source()));
                if (!LatencyAssertionConfig.isEffectivelyEnforced(hasExplicitThresholds)) {
                    sb.append(" (advisory)");
                }
            }

            // Append source if not just "explicit"
            if (pa.source() != null && !pa.source().equals("explicit")) {
                sb.append(" (").append(pa.source()).append(")");
            }

            if (pa.indicative()) {
                sb.append(" (indicative)");
            }
        }

        return sb.toString();
    }

    /**
     * Appends latency result to a StringBuilder for the console summary.
     *
     * @param sb the string builder to append to
     * @param lat the latency dimension from the verdict model
     */
    void appendTo(StringBuilder sb, LatencyDimension lat) {
        if (lat.skipped()) {
            return;
        }

        String rendered = render(lat);
        if (!rendered.isEmpty()) {
            sb.append(PUnitReporter.labelValueLn("Latency:", rendered.substring("Latency ".length())));
        }

        // Append caveats
        for (String caveat : lat.caveats()) {
            sb.append(PUnitReporter.labelValueLn("Note:", caveat));
        }
    }
}
