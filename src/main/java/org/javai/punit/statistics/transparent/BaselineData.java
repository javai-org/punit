package org.javai.punit.statistics.transparent;

import java.time.Instant;

/**
 * Baseline data for statistical explanation.
 *
 * <p>This record holds the baseline information needed to build a statistical
 * explanation without depending on the spec package.
 *
 * @param sourceFile The spec file path/name (e.g., "ShoppingUseCase.yaml")
 * @param generatedAt When the spec was generated
 * @param baselineSamples Number of samples in the baseline experiment
 * @param baselineSuccesses Number of successes in the baseline experiment
 */
public record BaselineData(
        String sourceFile,
        Instant generatedAt,
        int baselineSamples,
        int baselineSuccesses
) {
    /**
     * Returns the observed baseline rate.
     */
    public double baselineRate() {
        if (baselineSamples == 0) return 0.0;
        return (double) baselineSuccesses / baselineSamples;
    }

    /**
     * Returns true if this baseline data is present (non-zero samples).
     */
    public boolean hasData() {
        return baselineSamples > 0;
    }

    /**
     * Creates a baseline with no data (for legacy mode).
     */
    public static BaselineData empty() {
        return new BaselineData("(inline configuration)", null, 0, 0);
    }
}

