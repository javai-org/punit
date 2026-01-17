package org.javai.punit.ptest.engine;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.ptest.bernoulli.BernoulliFailureMessages;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;

/**
 * Holds the resolved test configuration for a probabilistic test.
 *
 * <p>This record contains all configuration values needed to execute a probabilistic
 * test, including sample counts, thresholds, budgets, and statistical context.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 *
 * @param samples The number of samples to execute
 * @param minPassRate The minimum pass rate threshold (0.0-1.0)
 * @param appliedMultiplier The sample multiplier that was applied (1.0 if none)
 * @param timeBudgetMs Time budget in milliseconds (0 for no budget)
 * @param tokenCharge Static token charge per sample
 * @param tokenBudget Token budget limit (0 for no budget)
 * @param tokenMode How tokens are tracked (NONE, STATIC, DYNAMIC)
 * @param onBudgetExhausted Behavior when budget is exhausted
 * @param onException How to handle exceptions during sample execution
 * @param maxExampleFailures Maximum failures to show in output
 * @param confidence Statistical confidence level (null for legacy mode)
 * @param baselineRate Baseline success rate (null for legacy mode)
 * @param baselineSamples Number of samples in baseline (null for legacy mode)
 * @param specId Specification identifier (null for legacy mode)
 * @param pacing Pacing configuration for rate limiting
 * @param transparentStats Configuration for transparent statistics output
 * @param thresholdOrigin Origin of the threshold (SLA, SLO, POLICY, etc.)
 * @param contractRef Reference to external contract document
 */
record TestConfiguration(
        int samples,
        double minPassRate,
        double appliedMultiplier,
        long timeBudgetMs,
        int tokenCharge,
        long tokenBudget,
        CostBudgetMonitor.TokenMode tokenMode,
        BudgetExhaustedBehavior onBudgetExhausted,
        ExceptionHandling onException,
        int maxExampleFailures,
        // Statistical context for failure messages (null for legacy/spec-less mode)
        Double confidence,
        Double baselineRate,
        Integer baselineSamples,
        String specId,
        // Pacing configuration
        PacingConfiguration pacing,
        // Transparent stats configuration
        TransparentStatsConfig transparentStats,
        // Provenance metadata
        ThresholdOrigin thresholdOrigin,
        String contractRef
) {
    /**
     * Returns true if a sample multiplier was applied.
     */
    boolean hasMultiplier() {
        return appliedMultiplier != 1.0;
    }

    /**
     * Returns true if a time budget is configured.
     */
    boolean hasTimeBudget() {
        return timeBudgetMs > 0;
    }

    /**
     * Returns true if a token budget is configured.
     */
    boolean hasTokenBudget() {
        return tokenBudget > 0;
    }

    /**
     * Returns true if pacing is configured.
     */
    boolean hasPacing() {
        return pacing != null && pacing.hasPacing();
    }

    /**
     * Returns true if full statistical context is available (spec-driven mode).
     */
    boolean hasStatisticalContext() {
        return confidence != null && baselineRate != null && baselineSamples != null && specId != null;
    }

    /**
     * Returns true if transparent stats output is enabled.
     */
    boolean hasTransparentStats() {
        return transparentStats != null && transparentStats.enabled();
    }

    /**
     * Returns true if any provenance information is specified.
     */
    boolean hasProvenance() {
        return hasThresholdOrigin() || hasContractRef();
    }

    /**
     * Returns true if thresholdOrigin is specified (not UNSPECIFIED).
     */
    boolean hasThresholdOrigin() {
        return thresholdOrigin != null && thresholdOrigin != ThresholdOrigin.UNSPECIFIED;
    }

    /**
     * Returns true if contractRef is specified (not null or empty).
     */
    boolean hasContractRef() {
        return contractRef != null && !contractRef.isEmpty();
    }

    /**
     * Creates a copy of this configuration with an updated minPassRate.
     *
     * <p>Used when the minPassRate is derived from a baseline after lazy selection.
     */
    TestConfiguration withMinPassRate(double newMinPassRate) {
        return new TestConfiguration(
                samples, newMinPassRate, appliedMultiplier, timeBudgetMs, tokenCharge, tokenBudget,
                tokenMode, onBudgetExhausted, onException, maxExampleFailures,
                confidence, baselineRate, baselineSamples, specId,
                pacing, transparentStats, thresholdOrigin, contractRef
        );
    }

    /**
     * Builds the statistical context for failure messages.
     */
    BernoulliFailureMessages.StatisticalContext buildStatisticalContext(
            double observedRate, int successes, int samplesExecuted) {
        if (hasStatisticalContext()) {
            return new BernoulliFailureMessages.StatisticalContext(
                    confidence,
                    observedRate,
                    successes,
                    samplesExecuted,
                    minPassRate,
                    baselineRate,
                    baselineSamples,
                    specId
            );
        } else {
            return BernoulliFailureMessages.StatisticalContext.forLegacyMode(
                    observedRate,
                    successes,
                    samplesExecuted,
                    minPassRate
            );
        }
    }
}

