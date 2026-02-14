package org.javai.punit.spec.baseline.covariate;

import java.util.List;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.ConformanceDetail;

/**
 * Renders warnings for covariate non-conformance.
 */
public final class CovariateWarningRenderer {

    /**
     * Renders a warning for non-conforming covariates.
     *
     * <p>Returns a {@link PUnitReporter.WarningContent} for routing through
     * {@link PUnitReporter#reportWarn(String, String)}.
     *
     * @param nonConforming the non-conforming covariate details
     * @param ambiguous true if the baseline selection was ambiguous
     * @return the warning content, or empty content if no warning needed
     */
    public PUnitReporter.WarningContent render(List<ConformanceDetail> nonConforming, boolean ambiguous) {
        if (nonConforming.isEmpty() && !ambiguous) {
            return new PUnitReporter.WarningContent("", "");
        }

        var sb = new StringBuilder();

        if (!nonConforming.isEmpty()) {
            sb.append("Statistical inference may be less reliable.\n\n");

            for (var detail : nonConforming) {
                sb.append("• ").append(detail.covariateKey());
                sb.append(": baseline=").append(detail.baselineValue().toCanonicalString());
                sb.append(", test=").append(detail.testValue().toCanonicalString());
                sb.append("\n");
            }
        }

        if (ambiguous) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("Multiple equally-suitable baselines existed. Selection may be non-deterministic.");
        }

        return new PUnitReporter.WarningContent("COVARIATE NON-CONFORMANCE", sb.toString());
    }

    /**
     * Renders a short warning suitable for inline display in verbose mode caveats.
     *
     * @param nonConforming the non-conforming covariate details
     * @return short warning message
     */
    public String renderShort(List<ConformanceDetail> nonConforming) {
        if (nonConforming.isEmpty()) {
            return "";
        }

        var keys = nonConforming.stream()
            .map(ConformanceDetail::covariateKey)
            .toList();

        return "Non-conforming covariates: " + String.join(", ", keys);
    }
}
