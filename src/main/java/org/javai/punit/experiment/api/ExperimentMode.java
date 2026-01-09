package org.javai.punit.experiment.api;

/**
 * Defines the execution mode for an experiment.
 *
 * <p>Experiments serve two distinct purposes that share execution machinery
 * but have different intents and outputs. Each mode has a sensible default
 * sample size accessible via {@link #getDefaultSampleSize()}.
 *
 * <h2>Mode Comparison</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    @Experiment Modes                             │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  Mode:     MEASURE                   │ EXPLORE                  │
 * │  Intent:   Precise estimation        │ Factor comparison        │
 * │  Configs:  1 (implicit)              │ N (from factor source)   │
 * │  Samples:  1000+ (default: 1000)     │ 1+/config (default: 1)   │
 * │  Output:   spec in specs/            │ specs in explorations/   │
 * │  Decision: "What's the true rate?"   │ "Which config is best?"  │
 * │  Task:     ./gradlew measure         │ ./gradlew explore        │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Note:</b> Mode is mandatory - you must explicitly choose MEASURE or EXPLORE.
 *
 * @see Experiment#mode()
 * @see FactorSource
 */
public enum ExperimentMode {
    
    /**
     * MEASURE mode establishes reliable statistics for a single configuration.
     *
     * <p>Use it when you want to:
     * <ul>
     *   <li>Measure the true success rate with high precision</li>
     *   <li>Generate an empirical spec for deriving test thresholds</li>
     *   <li>Establish confidence intervals for probabilistic assertions</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples:</b> 1000+ (default: 1000)</li>
     *   <li><b>Output:</b> Single spec in {@code src/test/resources/punit/specs/}</li>
     *   <li><b>Task:</b> {@code ./gradlew measure}</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @Experiment(mode = MEASURE, useCase = ShoppingUseCase.class, samples = 1000)
     * void measureShoppingSearch(ShoppingUseCase useCase, ResultCaptor captor) {
     *     captor.record(useCase.searchProducts("headphones"));
     * }
     * }</pre>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using &lt; 100 samples produces imprecise specs with wide confidence intervals.
     */
    MEASURE(1000),
    
    /**
     * EXPLORE mode compares multiple configurations to understand factor effects.
     *
     * <p>Use this mode when you want to:
     * <ul>
     *   <li>Compare different LLM models</li>
     *   <li>Evaluate temperature/prompt variations</li>
     *   <li>Find which configuration works best</li>
     * </ul>
     *
     * <h3>Typical Configuration</h3>
     * <ul>
     *   <li><b>Samples per config:</b> 1-10 (default: 1)</li>
     *   <li><b>Output:</b> Multiple specs in {@code src/test/resources/punit/explorations/}</li>
     *   <li><b>Task:</b> {@code ./gradlew explore}</li>
     * </ul>
     *
     * <h3>Typical Workflow</h3>
     * <p>Exploration usually happens in two phases:
     *
     * <p><b>Phase 1: "Which configs work at all?"</b>
     * <pre>{@code
     * @Experiment(mode = EXPLORE, samplesPerConfig = 1)
     * }</pre>
     * <p>Fast pass through all configurations to filter out broken ones.
     *
     * <p><b>Phase 2: "Which config is best?"</b>
     * <pre>{@code
     * @Experiment(mode = EXPLORE, samplesPerConfig = 10)
     * }</pre>
     * <p>More samples for remaining configs to gauge stochastic behaviors.
     *
     * <h3>Output</h3>
     * <p>One spec file per configuration in {@code explorations/}, enabling comparison via:
     * <ul>
     *   <li>IDE diff tools</li>
     *   <li>Command-line diff</li>
     *   <li>Future PUnit comparison tooling</li>
     * </ul>
     *
     * <h3>Anti-pattern</h3>
     * <p>Using &gt; 50 samples per config during exploration is wasteful.
     * Use MEASURE mode once you've chosen the best configuration.
     */
    EXPLORE(1);

    /**
     * The default number of samples for this mode when not explicitly specified.
     *
     * <ul>
     *   <li>{@link #MEASURE}: 1000 samples for statistically reliable specs</li>
     *   <li>{@link #EXPLORE}: 1 sample per config for fast initial filtering</li>
     * </ul>
     */
    private final int defaultSampleSize;

    ExperimentMode(int defaultSampleSize) {
        this.defaultSampleSize = defaultSampleSize;
    }

    /**
     * Returns the default sample size for this experiment mode.
     *
     * <p>This value is used when the sample count is not explicitly specified
     * in the {@link Experiment} annotation:
     * <ul>
     *   <li>{@link #MEASURE}: Returns 1000 - sufficient for tight confidence intervals</li>
     *   <li>{@link #EXPLORE}: Returns 1 - fast pass to filter broken configurations</li>
     * </ul>
     *
     * @return the default number of samples for this mode
     */
    public int getDefaultSampleSize() {
        return defaultSampleSize;
    }

    /**
     * Resolves the effective sample size, using the mode's default if not specified.
     *
     * <p>Resolution logic:
     * <ul>
     *   <li>If {@code tentativeSampleSize > 0}: returns the explicit value</li>
     *   <li>If {@code tentativeSampleSize <= 0}: returns this mode's {@link #getDefaultSampleSize()}</li>
     * </ul>
     *
     * <p>This allows annotation defaults of {@code 0} to act as "use mode default":
     * <pre>{@code
     * // Uses MEASURE default (1000)
     * @Experiment(mode = MEASURE, useCase = MyUseCase.class)
     *
     * // Explicit override (500 samples)
     * @Experiment(mode = MEASURE, useCase = MyUseCase.class, samples = 500)
     * }</pre>
     *
     * @param tentativeSampleSize the sample size from the annotation (0 = use default)
     * @return the effective sample size to use
     */
    public int getEffectiveSampleSize(int tentativeSampleSize) {
        return tentativeSampleSize > 0 ? tentativeSampleSize : defaultSampleSize;
    }
}
