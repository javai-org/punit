package org.javai.punit.ptest.bernoulli;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.ptest.strategy.ProbabilisticTestConfig;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;

/**
 * Configuration for Bernoulli trials-based probabilistic testing.
 *
 * <p>This record holds all configuration needed for the one-sided inference
 * testing approach where each sample is treated as a Bernoulli trial
 * (success/failure) and the observed pass rate is compared against a threshold.
 *
 * <p>Bernoulli-specific configuration includes:
 * <ul>
 *   <li>{@code minPassRate} - The minimum required pass rate threshold</li>
 *   <li>{@code confidence} - Statistical confidence level for threshold derivation</li>
 *   <li>{@code baselineRate} - The baseline success rate from measurement</li>
 *   <li>{@code baselineSamples} - Number of samples in baseline measurement</li>
 * </ul>
 *
 * @param samples number of samples to execute
 * @param minPassRate the minimum pass rate threshold (0.0-1.0)
 * @param appliedMultiplier the sample multiplier that was applied (1.0 if none)
 * @param timeBudgetMs time budget in milliseconds (0 for no budget)
 * @param tokenCharge static token charge per sample
 * @param tokenBudget token budget limit (0 for no budget)
 * @param tokenMode how tokens are tracked (NONE, STATIC, DYNAMIC)
 * @param onBudgetExhausted behavior when budget is exhausted
 * @param onException how to handle exceptions during sample execution
 * @param maxExampleFailures maximum failures to show in output
 * @param confidence statistical confidence level (null for legacy mode)
 * @param baselineRate baseline success rate (null for legacy mode)
 * @param baselineSamples number of samples in baseline (null for legacy mode)
 * @param specId specification identifier (null for legacy mode)
 * @param pacing pacing configuration for rate limiting
 * @param transparentStats configuration for transparent statistics output
 * @param thresholdOrigin origin of the threshold (SLA, SLO, POLICY, etc.)
 * @param contractRef reference to external contract document
 */
public record BernoulliTrialsConfig(
        int samples,
        double minPassRate,
        double appliedMultiplier,
        long timeBudgetMs,
        int tokenCharge,
        int tokenBudget,
        CostBudgetMonitor.TokenMode tokenMode,
        BudgetExhaustedBehavior onBudgetExhausted,
        ExceptionHandling onException,
        int maxExampleFailures,
        Double confidence,
        Double baselineRate,
        Integer baselineSamples,
        String specId,
        PacingConfiguration pacing,
        TransparentStatsConfig transparentStats,
        ThresholdOrigin thresholdOrigin,
        String contractRef
) implements ProbabilisticTestConfig {

    /**
     * Returns true if full statistical context is available (spec-driven mode).
     */
    public boolean hasStatisticalContext() {
        return confidence != null && baselineRate != null && baselineSamples != null && specId != null;
    }

    /**
     * Returns true if transparent stats output is enabled.
     */
    public boolean hasTransparentStats() {
        return transparentStats != null && transparentStats.enabled();
    }

    /**
     * Returns true if any provenance information is specified.
     */
    public boolean hasProvenance() {
        return hasThresholdOrigin() || hasContractRef();
    }

    /**
     * Returns true if thresholdOrigin is specified (not UNSPECIFIED).
     */
    public boolean hasThresholdOrigin() {
        return thresholdOrigin != null && thresholdOrigin != ThresholdOrigin.UNSPECIFIED;
    }

    /**
     * Returns true if contractRef is specified (not null or empty).
     */
    public boolean hasContractRef() {
        return contractRef != null && !contractRef.isEmpty();
    }

    /**
     * Returns true if a time budget is configured.
     */
    public boolean hasTimeBudget() {
        return timeBudgetMs > 0;
    }

    /**
     * Returns true if a token budget is configured.
     */
    public boolean hasTokenBudget() {
        return tokenBudget > 0;
    }

    /**
     * Creates a copy of this configuration with an updated minPassRate.
     *
     * <p>Used when the minPassRate is derived from a baseline after lazy selection.
     */
    public BernoulliTrialsConfig withMinPassRate(double newMinPassRate) {
        return new BernoulliTrialsConfig(
                samples, newMinPassRate, appliedMultiplier, timeBudgetMs, tokenCharge, tokenBudget,
                tokenMode, onBudgetExhausted, onException, maxExampleFailures,
                confidence, baselineRate, baselineSamples, specId,
                pacing, transparentStats, thresholdOrigin, contractRef
        );
    }

    /**
     * Builds the statistical context for failure messages.
     */
    public BernoulliFailureMessages.StatisticalContext buildStatisticalContext(
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
