package org.javai.punit.spec.baseline.covariate;

import java.util.List;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.ConformanceDetail;

/**
 * Renders warnings for covariate non-conformance.
 */
public final class CovariateWarningRenderer {

    /**
     * Renders a warning message for non-conforming covariates.
     *
     * @param nonConforming the non-conforming covariate details
     * @param ambiguous true if the baseline selection was ambiguous
     * @return the warning message, or empty string if no warning needed
     */
    public String render(List<ConformanceDetail> nonConforming, boolean ambiguous) {
        if (nonConforming.isEmpty() && !ambiguous) {
            return "";
        }

        var sb = new StringBuilder();
        
        if (!nonConforming.isEmpty()) {
            sb.append("⚠️ COVARIATE NON-CONFORMANCE\n");
            sb.append("Statistical inference may be less reliable.\n\n");

            for (var detail : nonConforming) {
                sb.append("  • ").append(detail.covariateKey());
                sb.append(": baseline=").append(detail.baselineValue().toCanonicalString());
                sb.append(", test=").append(detail.testValue().toCanonicalString());
                sb.append("\n");
            }
        }

        if (ambiguous) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("⚠️ Multiple equally-suitable baselines existed. Selection may be non-deterministic.\n");
        }

        return sb.toString();
    }

    /**
     * Renders a short warning suitable for inline display.
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

        return "⚠️ Non-conforming covariates: " + String.join(", ", keys);
    }
}

