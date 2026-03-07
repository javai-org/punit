package org.javai.punit.ptest.engine;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.model.SuccessCriteria;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.DerivedThreshold;
import org.javai.punit.statistics.OperationalApproach;
import org.javai.punit.statistics.SampleSizeCalculator;
import org.javai.punit.statistics.SampleSizeRequirement;
import org.javai.punit.statistics.ThresholdDeriver;

/**
 * Resolves a complete configuration for a spec-driven probabilistic test,
 * including statistically-derived thresholds and computed sample sizes.
 *
 * <p>This record integrates the statistics engine with the annotation processing
 * to produce a fully resolved configuration ready for test execution.
 *
 * @param approachResolver Resolves which operational approach is being used
 * @param thresholdDeriver Derives thresholds from baseline data
 * @param sampleSizeCalculator Computes required sample sizes via power analysis
 */
public record StatisticsAwareConfiguration(
        OperationalApproachResolver approachResolver,
        ThresholdDeriver thresholdDeriver,
        SampleSizeCalculator sampleSizeCalculator
) {

    /**
     * Creates a new instance with default statistics components.
     *
     * @return A new StatisticsAwareConfiguration with default dependencies
     */
    public static StatisticsAwareConfiguration createDefault() {
        OperationalApproachResolver resolver = new OperationalApproachResolver();
        BinomialProportionEstimator estimator = new BinomialProportionEstimator();
        ThresholdDeriver deriver = new ThresholdDeriver(estimator);
        SampleSizeCalculator calculator = new SampleSizeCalculator();
        return new StatisticsAwareConfiguration(resolver, deriver, calculator);
    }

    /**
     * Resolves a complete configuration from annotation and spec.
     *
     * @param annotation The @ProbabilisticTest annotation
     * @param spec The loaded specification (may be null for spec-less mode)
     * @param testMethodName Name of the test method (for error messages)
     * @return The fully resolved configuration with computed statistics
     * @throws ProbabilisticTestConfigurationException if parameters are invalid
     */
    public ResolvedStatisticalConfiguration resolve(
            ProbabilisticTest annotation,
            ExecutionSpecification spec,
            String testMethodName) {

        boolean hasSpec = spec != null;

        // Validate and resolve the operational approach
        // This enforces: exactly one approach must be specified
        // For spec-less mode, only THRESHOLD_FIRST is valid
        OperationalApproachResolver.ResolvedApproach approach =
                approachResolver.resolve(annotation, hasSpec);

        // Spec-less mode: use explicit parameters (only THRESHOLD_FIRST reaches here)
        if (approach.isSpecless()) {
            return createSpeclessConfiguration(annotation, approach);
        }

        // Spec-driven mode: validate spec has baseline data
        if (hasSpec && !spec.hasEmpiricalBasis()) {
            throw new ProbabilisticTestConfigurationException(String.format("""
                
                ═══════════════════════════════════════════════════════════════════════════
                ❌ SPECIFICATION ERROR: Missing Baseline Data
                ═══════════════════════════════════════════════════════════════════════════
                
                Specification '%s' does not contain baseline data.
                
                Baseline data (samples and successes) is required for statistical
                threshold derivation. Ensure the spec file includes:
                
                  baseline:
                    samples: 1000
                    successes: 951
                
                This data should be populated when the baseline is approved.
                
                ═══════════════════════════════════════════════════════════════════════════
                """, spec.getUseCaseId()));
        }

        // Derive threshold and sample count based on operational approach
        return switch (approach.approach()) {
            case SAMPLE_SIZE_FIRST -> resolveSampleSizeFirst(annotation, spec, approach);
            case CONFIDENCE_FIRST -> resolveConfidenceFirst(annotation, spec, approach);
            case THRESHOLD_FIRST -> resolveThresholdFirst(annotation, spec, approach);
        };
    }

    private ResolvedStatisticalConfiguration resolveSampleSizeFirst(
            ProbabilisticTest annotation,
            ExecutionSpecification spec,
            OperationalApproachResolver.ResolvedApproach approach) {

        DerivedThreshold derivedThreshold = thresholdDeriver.deriveSampleSizeFirst(
                spec.getBaselineSamples(),
                spec.getBaselineSuccesses(),
                approach.samples(),
                approach.confidence()
        );

        return new ResolvedStatisticalConfiguration(
                approach.samples(),
                derivedThreshold.value(),
                annotation.timeBudgetMs(),
                annotation.tokenCharge(),
                annotation.tokenBudget(),
                annotation.onBudgetExhausted(),
                annotation.onException(),
                annotation.maxExampleFailures(),
                derivedThreshold,
                spec.getSuccessCriteria()
        );
    }

    private ResolvedStatisticalConfiguration resolveConfidenceFirst(
            ProbabilisticTest annotation,
            ExecutionSpecification spec,
            OperationalApproachResolver.ResolvedApproach approach) {

        // Calculate required sample size via power analysis
        SampleSizeRequirement sampleReq = sampleSizeCalculator.calculateForPower(
                spec.getObservedRate(),
                approach.minDetectableEffect(),
                approach.confidence(),
                approach.power()
        );

        int computedSamples = sampleReq.requiredSamples();

        // Derive threshold with the computed sample size
        DerivedThreshold derivedThreshold = thresholdDeriver.deriveSampleSizeFirst(
                spec.getBaselineSamples(),
                spec.getBaselineSuccesses(),
                computedSamples,
                approach.confidence()
        );

        // Override the approach to indicate it was derived via Confidence-First
        DerivedThreshold thresholdWithApproach = new DerivedThreshold(
                derivedThreshold.value(),
                OperationalApproach.CONFIDENCE_FIRST,
                derivedThreshold.context(),
                derivedThreshold.isStatisticallySound()
        );

        return new ResolvedStatisticalConfiguration(
                computedSamples,
                thresholdWithApproach.value(),
                annotation.timeBudgetMs(),
                annotation.tokenCharge(),
                annotation.tokenBudget(),
                annotation.onBudgetExhausted(),
                annotation.onException(),
                annotation.maxExampleFailures(),
                thresholdWithApproach,
                spec.getSuccessCriteria()
        );
    }

    private ResolvedStatisticalConfiguration resolveThresholdFirst(
            ProbabilisticTest annotation,
            ExecutionSpecification spec,
            OperationalApproachResolver.ResolvedApproach approach) {

        DerivedThreshold derivedThreshold = thresholdDeriver.deriveThresholdFirst(
                spec.getBaselineSamples(),
                spec.getBaselineSuccesses(),
                approach.samples(),
                approach.minPassRate()
        );

        // Create warning if statistically unsound (caller decides how to report it)
        ConfigurationWarning warning = null;
        if (!derivedThreshold.isStatisticallySound()) {
            warning = ConfigurationWarning.statisticallyUnsound(
                    spec.getUseCaseId(),
                    approach.minPassRate(),
                    spec.getObservedRate(),
                    derivedThreshold.context().confidence());
        }

        return new ResolvedStatisticalConfiguration(
                approach.samples(),
                derivedThreshold.value(),
                annotation.timeBudgetMs(),
                annotation.tokenCharge(),
                annotation.tokenBudget(),
                annotation.onBudgetExhausted(),
                annotation.onException(),
                annotation.maxExampleFailures(),
                derivedThreshold,
                spec.getSuccessCriteria(),
                warning
        );
    }

    /**
     * Creates configuration for spec-less mode (explicit threshold, no baseline data).
     *
     * <p>In spec-less mode, only THRESHOLD_FIRST is valid, so the approach always
     * has an explicit minPassRate.
     */
    private ResolvedStatisticalConfiguration createSpeclessConfiguration(
            ProbabilisticTest annotation,
            OperationalApproachResolver.ResolvedApproach approach) {

        // Spec-less mode always uses THRESHOLD_FIRST with explicit minPassRate
        // (enforced by OperationalApproachResolver)
        return new ResolvedStatisticalConfiguration(
                approach.samples(),
                approach.minPassRate(),
                annotation.timeBudgetMs(),
                annotation.tokenCharge(),
                annotation.tokenBudget(),
                annotation.onBudgetExhausted(),
                annotation.onException(),
                annotation.maxExampleFailures(),
                null, // No derived threshold in spec-less mode
                null  // No success criteria in spec-less mode
        );
    }

    /**
     * Fully resolved configuration with statistical context.
     */
    public record ResolvedStatisticalConfiguration(
            int samples,
            double minPassRate,
            long timeBudgetMs,
            int tokenCharge,
            long tokenBudget,
            BudgetExhaustedBehavior onBudgetExhausted,
            ExceptionHandling onException,
            int maxExampleFailures,
            DerivedThreshold derivedThreshold,
            SuccessCriteria successCriteria,
            ConfigurationWarning warning
    ) {
        /**
         * Constructor without warning (for backwards compatibility).
         */
        public ResolvedStatisticalConfiguration(
                int samples,
                double minPassRate,
                long timeBudgetMs,
                int tokenCharge,
                long tokenBudget,
                BudgetExhaustedBehavior onBudgetExhausted,
                ExceptionHandling onException,
                int maxExampleFailures,
                DerivedThreshold derivedThreshold,
                SuccessCriteria successCriteria) {
            this(samples, minPassRate, timeBudgetMs, tokenCharge, tokenBudget,
                 onBudgetExhausted, onException, maxExampleFailures,
                 derivedThreshold, successCriteria, null);
        }

        /**
         * Returns true if this configuration has a warning.
         */
        public boolean hasWarning() {
            return warning != null;
        }

        /**
         * Returns true if this is a spec-driven test with statistical threshold.
         */
        public boolean isSpecDriven() {
            return derivedThreshold != null;
        }

        /**
         * Returns the operational approach used, or null for spec-less mode.
         */
        public OperationalApproach getApproach() {
            return derivedThreshold != null ? derivedThreshold.approach() : null;
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
    }
}
