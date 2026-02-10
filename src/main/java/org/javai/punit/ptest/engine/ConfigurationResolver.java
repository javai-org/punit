package org.javai.punit.ptest.engine;

import java.lang.reflect.Method;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.registry.SpecificationIntegrityException;
import org.javai.punit.spec.registry.SpecificationNotFoundException;
import org.javai.punit.spec.registry.SpecificationRegistry;
import org.javai.punit.statistics.StatisticalDefaults;

/**
 * Resolves effective configuration for a probabilistic test by merging values from
 * multiple sources with defined precedence.
 *
 * <h2>Precedence (highest to lowest)</h2>
 * <ol>
 *   <li>System property (e.g., {@code -Dpunit.samples=10})</li>
 *   <li>Environment variable (e.g., {@code PUNIT_SAMPLES=10})</li>
 *   <li>Annotation value (e.g., {@code @ProbabilisticTest(samples = 100)})</li>
 *   <li>Framework default</li>
 * </ol>
 *
 * <p>This allows different configurations per environment (PR builds, nightly, local dev)
 * without code changes.
 */
public class ConfigurationResolver {

    private static final Logger logger = LogManager.getLogger(ConfigurationResolver.class);

    // System property names
    public static final String PROP_SAMPLES = "punit.samples";
    public static final String PROP_MIN_PASS_RATE = "punit.minPassRate";
    public static final String PROP_SAMPLES_MULTIPLIER = "punit.samplesMultiplier";
    public static final String PROP_TIME_BUDGET_MS = "punit.timeBudgetMs";
    public static final String PROP_TOKEN_CHARGE = "punit.tokenCharge";
    public static final String PROP_TOKEN_BUDGET = "punit.tokenBudget";

    // Environment variable names
    public static final String ENV_SAMPLES = "PUNIT_SAMPLES";
    public static final String ENV_MIN_PASS_RATE = "PUNIT_MIN_PASS_RATE";
    public static final String ENV_SAMPLES_MULTIPLIER = "PUNIT_SAMPLES_MULTIPLIER";
    public static final String ENV_TIME_BUDGET_MS = "PUNIT_TIME_BUDGET_MS";
    public static final String ENV_TOKEN_CHARGE = "PUNIT_TOKEN_CHARGE";
    public static final String ENV_TOKEN_BUDGET = "PUNIT_TOKEN_BUDGET";

    // Framework defaults
    public static final int DEFAULT_SAMPLES = 100;
    public static final double DEFAULT_SAMPLES_MULTIPLIER = 1.0;
    public static final long DEFAULT_TIME_BUDGET_MS = 0;
    public static final int DEFAULT_TOKEN_CHARGE = 0;
    public static final long DEFAULT_TOKEN_BUDGET = 0;
    public static final double DEFAULT_THRESHOLD_CONFIDENCE = StatisticalDefaults.DEFAULT_CONFIDENCE;

    /**
     * Resolves the effective configuration for a test method.
     *
     * @param testMethod the test method with @ProbabilisticTest annotation
     * @return the resolved configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public ResolvedConfiguration resolve(Method testMethod) {
        ProbabilisticTest annotation = testMethod.getAnnotation(ProbabilisticTest.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Method " + testMethod.getName() + " is not annotated with @ProbabilisticTest");
        }

        return resolve(annotation, testMethod.getName());
    }

    /**
     * Resolves the effective configuration from an annotation.
     *
     * @param annotation the @ProbabilisticTest annotation
     * @param contextName name for error messages (e.g., method name)
     * @return the resolved configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public ResolvedConfiguration resolve(ProbabilisticTest annotation, String contextName) {
        // Resolve samples
        int baseSamples = resolveInt(
                PROP_SAMPLES, ENV_SAMPLES,
                annotation.samples(),
                DEFAULT_SAMPLES);

        // Validate base samples before multiplier (explicit 0 or negative should fail)
        if (baseSamples <= 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": samples must be >= 1, but resolved to " + baseSamples);
        }

        // Apply multiplier
        double multiplier = resolveDouble(
                PROP_SAMPLES_MULTIPLIER, ENV_SAMPLES_MULTIPLIER,
                DEFAULT_SAMPLES_MULTIPLIER,
                DEFAULT_SAMPLES_MULTIPLIER);

        int effectiveSamples = (int) Math.round(baseSamples * multiplier);
        // Ensure at least 1 sample after multiplier (small multiplier shouldn't reduce below 1)
        effectiveSamples = Math.max(1, effectiveSamples);

        // Resolve minPassRate from explicit sources only
        // Priority:
        // 1. System property or env var override
        // 2. Explicit annotation value (not NaN)
        // 3. Leave as NaN (will be derived from baseline by ProbabilisticTestValidator)
        //
        // NOTE: We do NOT derive threshold from baseline here. That is done after
        // baseline selection by ProbabilisticTestExtension. ProbabilisticTestValidator
        // is the single source of truth for configuration validity.
        double annotationMinPassRate = annotation.minPassRate();
        
        // Determine if we have a spec reference (via useCase class)
        Optional<String> specIdOpt = resolveSpecId(annotation);
        
        double minPassRate;
        
        // Check for system property or env var override first
        String sysPropValue = System.getProperty(PROP_MIN_PASS_RATE);
        String envValue = getEnvironmentVariable(ENV_MIN_PASS_RATE);
        
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                minPassRate = Double.parseDouble(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid " + PROP_MIN_PASS_RATE + " value: '" + sysPropValue + "' is not a valid number");
            }
        } else if (envValue != null && !envValue.isEmpty()) {
            try {
                minPassRate = Double.parseDouble(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid " + ENV_MIN_PASS_RATE + " value: '" + envValue + "' is not a valid number");
            }
        } else if (!Double.isNaN(annotationMinPassRate)) {
            // Explicit annotation value
            minPassRate = annotationMinPassRate;
        } else {
            // Leave as NaN - will be derived from baseline or validated by ProbabilisticTestValidator
            minPassRate = Double.NaN;
        }

        // Validate minPassRate range (only if explicitly set)
        if (!Double.isNaN(minPassRate)) {
            validateMinPassRate(minPassRate, contextName);
        }

        // Resolve budget parameters
        long timeBudgetMs = resolveLong(
                PROP_TIME_BUDGET_MS, ENV_TIME_BUDGET_MS,
                annotation.timeBudgetMs(),
                DEFAULT_TIME_BUDGET_MS);

        int tokenCharge = resolveInt(
                PROP_TOKEN_CHARGE, ENV_TOKEN_CHARGE,
                annotation.tokenCharge(),
                DEFAULT_TOKEN_CHARGE);

        long tokenBudget = resolveLong(
                PROP_TOKEN_BUDGET, ENV_TOKEN_BUDGET,
                annotation.tokenBudget(),
                DEFAULT_TOKEN_BUDGET);

        // Validate budget parameters
        validateBudgets(timeBudgetMs, tokenCharge, tokenBudget, contextName);

        // Get other annotation values (no overrides for these)
        BudgetExhaustedBehavior onBudgetExhausted = annotation.onBudgetExhausted();
        ExceptionHandling onException = annotation.onException();
        int maxExampleFailures = annotation.maxExampleFailures();

        // Get provenance values (annotation-only, no override)
        ThresholdOrigin thresholdOrigin = annotation.thresholdOrigin();
        String contractRef = annotation.contractRef();

        // Resolve intent (annotation-only, no override)
        TestIntent intent = annotation.intent();

        // Validate confidence parameters early (Req 7b)
        validateConfidence(annotation.thresholdConfidence(), "thresholdConfidence", contextName);
        validateConfidence(annotation.confidence(), "confidence", contextName);

        // Resolve confidence for feasibility evaluation:
        // 1. annotation.thresholdConfidence() if not NaN
        // 2. framework default: 0.95
        double resolvedConfidence = !Double.isNaN(annotation.thresholdConfidence())
                ? annotation.thresholdConfidence()
                : DEFAULT_THRESHOLD_CONFIDENCE;

        return new ResolvedConfiguration(
                effectiveSamples,
                minPassRate,
                multiplier,
                timeBudgetMs,
                tokenCharge,
                tokenBudget,
                onBudgetExhausted,
                onException,
                maxExampleFailures,
                null, null, null,  // confidence, baselineRate, baselineSamples (not resolved here)
                specIdOpt.orElse(null),  // specId from use case class
                thresholdOrigin,
                contractRef,
                intent,
                resolvedConfidence
        );
    }

    /**
     * Resolves an integer value with precedence: system prop > env var > annotation > default.
     */
    private int resolveInt(String sysProp, String envVar, int annotationValue, int ignored) {
        // 1. System property (highest priority)
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Integer.parseInt(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        // 2. Environment variable
        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        // 3. Annotation value
        return annotationValue;
    }

    /**
     * Resolves a long value with precedence: system prop > env var > annotation > default.
     */
    private long resolveLong(String sysProp, String envVar, long annotationValue, long ignored) {
        // 1. System property (highest priority)
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Long.parseLong(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        // 2. Environment variable
        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Long.parseLong(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        // 3. Annotation value
        return annotationValue;
    }

    /**
     * Resolves a double value with precedence: system prop > env var > annotation > default.
     */
    private double resolveDouble(String sysProp, String envVar, double annotationValue, double ignored) {
        // 1. System property (highest priority)
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Double.parseDouble(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        // 2. Environment variable
        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Double.parseDouble(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        // 3. Annotation value
        return annotationValue;
    }

    /**
     * Gets an environment variable. Extracted for testability.
     */
    protected String getEnvironmentVariable(String name) {
        return System.getenv(name);
    }

    /**
     * Validates a confidence parameter is in the open interval (0, 1), or NaN (unset).
     *
     * @param value the confidence value to validate
     * @param paramName the parameter name for error messages
     * @param contextName the test name for error messages
     */
    private void validateConfidence(double value, String paramName, String contextName) {
        if (Double.isNaN(value)) {
            return; // NaN means unset — valid
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(String.format(
                    "%s = %s is invalid for %s: " +
                    "\u03b1 = 1 renders the test meaningless. Use a value in (0, 1), e.g. 0.95.",
                    paramName, value, contextName));
        }
        if (value >= 1.0) {
            throw new IllegalArgumentException(String.format(
                    "%s = %s is invalid for %s: " +
                    "\u03b1 = 0 makes finite-sample inference vacuous. Use a value in (0, 1), e.g. 0.95.",
                    paramName, value, contextName));
        }
    }

    /**
     * Validates the minPassRate configuration.
     */
    private void validateMinPassRate(double minPassRate, String contextName) {
        if (minPassRate < 0.0 || minPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": minPassRate must be in range [0.0, 1.0], but resolved to " + minPassRate);
        }
    }

    /**
     * Validates budget parameters.
     */
    private void validateBudgets(long timeBudgetMs, int tokenCharge, long tokenBudget, String contextName) {
        if (timeBudgetMs < 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": timeBudgetMs must be >= 0, but was " + timeBudgetMs);
        }
        if (tokenCharge < 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": tokenCharge must be >= 0, but was " + tokenCharge);
        }
        if (tokenBudget < 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration for " + contextName +
                    ": tokenBudget must be >= 0, but was " + tokenBudget);
        }
    }

    /**
     * Holds the resolved configuration values.
     */
    public record ResolvedConfiguration(
            int samples,
            double minPassRate,
            double appliedMultiplier,
            long timeBudgetMs,
            int tokenCharge,
            long tokenBudget,
            BudgetExhaustedBehavior onBudgetExhausted,
            ExceptionHandling onException,
            int maxExampleFailures,
            // Statistical context for failure messages (nullable for inline threshold mode)
            Double confidence,
            Double baselineRate,
            Integer baselineSamples,
            String specId,
            // Provenance metadata (annotation-only, no override)
            ThresholdOrigin thresholdOrigin,
            String contractRef,
            // Intent declaration
            TestIntent intent,
            double resolvedConfidence
    ) {
        /**
         * Constructor for backward compatibility - creates configuration without statistical context or provenance.
         */
        public ResolvedConfiguration(
                int samples,
                double minPassRate,
                double appliedMultiplier,
                long timeBudgetMs,
                int tokenCharge,
                long tokenBudget,
                BudgetExhaustedBehavior onBudgetExhausted,
                ExceptionHandling onException,
                int maxExampleFailures) {
            this(samples, minPassRate, appliedMultiplier, timeBudgetMs, tokenCharge, tokenBudget,
                    onBudgetExhausted, onException, maxExampleFailures,
                    null, null, null, null,
                    ThresholdOrigin.UNSPECIFIED, "",
                    TestIntent.VERIFICATION, DEFAULT_THRESHOLD_CONFIDENCE);
        }

        /**
         * Returns true if the intent is SMOKE.
         */
        public boolean isSmoke() {
            return intent == TestIntent.SMOKE;
        }

        /**
         * Returns true if the intent is VERIFICATION.
         */
        public boolean isVerification() {
            return intent == TestIntent.VERIFICATION;
        }

        /**
         * Returns true if a multiplier was applied (not 1.0).
         */
        public boolean hasMultiplier() {
            return appliedMultiplier != 1.0;
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
         * Returns true if static token charging is configured.
         */
        public boolean hasStaticTokenCharge() {
            return tokenCharge > 0;
        }

        /**
         * Returns true if this configuration has full statistical context for qualified failure messages.
         */
        public boolean hasStatisticalContext() {
            return confidence != null && baselineRate != null && baselineSamples != null && specId != null;
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
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPEC RESOLUTION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves the spec ID from the annotation.
     *
     * <p>Priority:
     * <ol>
     *   <li>If useCase class is specified (not Void.class), derive ID from class</li>
     *   <li>Otherwise, use explicit spec ID if provided</li>
     * </ol>
     *
     * @param annotation the test annotation
     * @return an Optional containing the spec ID, or empty if none specified
     */
    private Optional<String> resolveSpecId(ProbabilisticTest annotation) {
        // Use case class reference determines spec ID
        Class<?> useCaseClass = annotation.useCase();
        if (useCaseClass != null && useCaseClass != Void.class) {
            return Optional.of(UseCaseProvider.resolveId(useCaseClass));
        }

        return Optional.empty();
    }

    /**
     * Loads an ExecutionSpecification by its ID.
     *
     * <p>This method is used both for loading minPassRate and for factor consistency
     * validation. It handles all error cases gracefully.
     *
     * @param specId the specification ID
     * @return the loaded spec, or null if not found or loading failed
     * @throws SpecificationIntegrityException if spec file fails integrity validation
     */
    public Optional<ExecutionSpecification> loadSpec(String specId) {
        if (specId == null || specId.isEmpty()) {
            return Optional.empty();
        }
        try {
            SpecificationRegistry registry = new SpecificationRegistry();
            return Optional.of(registry.resolve(specId));
        } catch (SpecificationIntegrityException e) {
            // Integrity failures must propagate - indicates tampering or corruption
            throw e;
        } catch (SpecificationNotFoundException e) {
            // Spec not found
            return Optional.empty();
        } catch (Exception e) {
            // Log warning but don't fail
            logger.warn("Warning: Failed to load spec {}: {}", specId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves the spec ID from a @ProbabilisticTest annotation.
     *
     * <p>Exposed for factor consistency validation.
     *
     * @param annotation the test annotation
     * @return an Optional containing the spec ID, or empty if none specified
     */
    public Optional<String> resolveSpecIdFromAnnotation(ProbabilisticTest annotation) {
        return resolveSpecId(annotation);
    }
}
