package org.javai.punit.engine;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;

import java.lang.reflect.Method;

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
    public static final double DEFAULT_MIN_PASS_RATE = 0.95;
    public static final double DEFAULT_SAMPLES_MULTIPLIER = 1.0;
    public static final long DEFAULT_TIME_BUDGET_MS = 0;
    public static final int DEFAULT_TOKEN_CHARGE = 0;
    public static final long DEFAULT_TOKEN_BUDGET = 0;
    public static final int DEFAULT_MAX_EXAMPLE_FAILURES = 5;

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

        // Resolve minPassRate
        // Note: minPassRate can be NaN when using statistical approaches (Sample-Size-First or Confidence-First)
        // In those cases, the threshold is derived at runtime from baseline data.
        // However, for legacy mode (no spec), NaN must fall back to the default.
        double annotationMinPassRate = annotation.minPassRate();
        boolean hasSpec = annotation.spec() != null && !annotation.spec().isEmpty();
        double minPassRate;
        
        if (Double.isNaN(annotationMinPassRate)) {
            // Check for system property or env var override
            String sysPropValue = System.getProperty(PROP_MIN_PASS_RATE);
            String envValue = getEnvironmentVariable(ENV_MIN_PASS_RATE);
            
            if (sysPropValue != null && !sysPropValue.isEmpty()) {
                minPassRate = Double.parseDouble(sysPropValue.trim());
            } else if (envValue != null && !envValue.isEmpty()) {
                minPassRate = Double.parseDouble(envValue.trim());
            } else if (hasSpec) {
                // Keep NaN to indicate it should be derived from spec
                minPassRate = Double.NaN;
            } else {
                // Legacy mode (no spec): use default
                minPassRate = DEFAULT_MIN_PASS_RATE;
            }
        } else {
            minPassRate = resolveDouble(
                    PROP_MIN_PASS_RATE, ENV_MIN_PASS_RATE,
                    annotationMinPassRate,
                    DEFAULT_MIN_PASS_RATE);
        }

        // Validate minPassRate (only if set)
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

        return new ResolvedConfiguration(
                effectiveSamples,
                minPassRate,
                multiplier,
                timeBudgetMs,
                tokenCharge,
                tokenBudget,
                onBudgetExhausted,
                onException,
                maxExampleFailures
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
            // Statistical context for failure messages (nullable for legacy mode)
            Double confidence,
            Double baselineRate,
            Integer baselineSamples,
            String specId
    ) {
        /**
         * Constructor for backward compatibility - creates configuration without statistical context.
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
                    null, null, null, null);
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
    }
}
