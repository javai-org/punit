package org.javai.punit.ptest.strategy;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;

/**
 * Interface for probabilistic test configuration.
 *
 * <p>This interface defines the common configuration properties shared by
 * all probabilistic test strategies. Strategy-specific configuration is
 * defined in the implementing classes.
 *
 * <p>The primary implementation is {@link org.javai.punit.ptest.bernoulli.BernoulliTrialsConfig}
 * for one-sided inference testing based on Bernoulli trials.
 *
 * @see org.javai.punit.ptest.bernoulli.BernoulliTrialsConfig
 */
public interface ProbabilisticTestConfig {

    /**
     * Number of samples to execute.
     */
    int samples();

    /**
     * Time budget in milliseconds (0 = unlimited).
     */
    long timeBudgetMs();

    /**
     * Token charge per sample (for static token mode).
     */
    int tokenCharge();

    /**
     * Total token budget (0 = unlimited).
     */
    int tokenBudget();

    /**
     * Token charging mode.
     */
    CostBudgetMonitor.TokenMode tokenMode();

    /**
     * Behavior when budget is exhausted.
     */
    BudgetExhaustedBehavior onBudgetExhausted();

    /**
     * How to handle exceptions thrown by samples.
     */
    ExceptionHandling onException();

    /**
     * Maximum number of example failures to retain.
     */
    int maxExampleFailures();

    /**
     * Pacing configuration (rate limiting).
     */
    PacingConfiguration pacing();

    /**
     * Transparent statistics configuration.
     */
    TransparentStatsConfig transparentStats();

    /**
     * The origin of the threshold (annotation, baseline, etc.).
     */
    ThresholdOrigin thresholdOrigin();

    /**
     * Reference to the contract specification, if any.
     */
    String contractRef();

    /**
     * Specification ID, if derived from a baseline.
     */
    String specId();

    /**
     * Whether pacing is configured.
     */
    default boolean hasPacing() {
        return pacing() != null && pacing().hasPacing();
    }

    /**
     * Whether a sample multiplier was applied.
     */
    default boolean hasMultiplier() {
        return appliedMultiplier() > 1.0;
    }

    /**
     * The multiplier applied to samples (1.0 = no multiplier).
     */
    double appliedMultiplier();
}
