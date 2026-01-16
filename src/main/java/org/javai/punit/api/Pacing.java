package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares pacing constraints for a probabilistic test or experiment.
 *
 * <p>Pacing constraints inform the framework how to schedule sample execution
 * to stay within API rate limits. Unlike budget guardrails (which terminate
 * execution when exceeded), pacing constraints proactively control execution
 * pace to prevent hitting limits.
 *
 * <h2>Design Philosophy</h2>
 * <p>Pacing is fundamentally different from guardrails:
 * <ul>
 *   <li><b>Guardrails</b>: Reactive — "Stop if we exceed X"</li>
 *   <li><b>Pacing</b>: Proactive — "Use X to compute optimal pace"</li>
 * </ul>
 *
 * <p>Pacing constraints are inputs to a scheduling algorithm that computes
 * inter-request delays and concurrency levels to stay within limits while
 * maximizing throughput.
 *
 * <h2>Constraint Composition</h2>
 * <p>When multiple constraints are specified, the framework computes the
 * <b>most restrictive</b> effective pacing (highest delay wins).
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple delay-based pacing:</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100)
 * @Pacing(minMsPerSample = 500)
 * void testWithHalfSecondDelay() { ... }
 * }</pre>
 *
 * <h3>Rate-limited API:</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 200)
 * @Pacing(maxRequestsPerMinute = 60, maxConcurrentRequests = 3)
 * void testWithRateLimits() { ... }
 * }</pre>
 *
 * <h3>Combined constraints (most restrictive wins):</h3>
 * <pre>{@code
 * @ProbabilisticTest(samples = 100)
 * @Pacing(
 *     maxRequestsPerMinute = 60,
 *     maxRequestsPerSecond = 2,  // More restrictive: 500ms vs 1000ms
 *     maxConcurrentRequests = 5
 * )
 * void testWithMultipleConstraints() { ... }
 * }</pre>
 *
 * <h2>Experiments</h2>
 * <p>Pacing is equally applicable to {@link Experiment} methods. Experiments
 * are often long-running with many samples, making rate limit management
 * essential.
 *
 * <h3>MEASURE mode experiment with pacing:</h3>
 * <pre>{@code
 * @Experiment(mode = ExperimentMode.MEASURE, samples = 500)
 * @Pacing(maxRequestsPerMinute = 60)
 * void measureWithPacing(ResultCaptor<String> captor) { ... }
 * }</pre>
 *
 * <h3>EXPLORE mode with continuous pacing:</h3>
 * <p>In EXPLORE mode, pacing is applied <b>continuously</b> across all
 * factor combinations—not reset at each configuration boundary. This ensures
 * experiments with many configurations but few samples per config don't
 * overwhelm rate limits.
 *
 * <pre>{@code
 * @Experiment(mode = ExperimentMode.EXPLORE, samplesPerConfig = 5)
 * @Pacing(maxRequestsPerMinute = 30)
 * void exploreWithPacing(
 *     @Factor({"gpt-4", "gpt-3.5"}) String model,
 *     ResultCaptor<String> captor
 * ) { ... }
 * }</pre>
 *
 * <h3>Time budget and pacing interaction:</h3>
 * <p>If the total pacing delay would exceed the configured time budget,
 * a warning is logged at experiment start. The experiment proceeds but
 * may be terminated by the time budget before all samples complete.
 *
 * @see ProbabilisticTest
 * @see Experiment
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pacing {

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE-BASED CONSTRAINTS
    // Framework computes minimum delay from these
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum requests per second.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 1000 / maxRequestsPerSecond} ms
     *
     * @return the maximum RPS, or 0 for unlimited
     */
    double maxRequestsPerSecond() default 0;

    /**
     * Maximum requests per minute.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 60000 / maxRequestsPerMinute} ms
     *
     * <p>This is the most common constraint for LLM APIs (OpenAI, Anthropic, etc.).
     *
     * @return the maximum RPM, or 0 for unlimited
     */
    double maxRequestsPerMinute() default 0;

    /**
     * Maximum requests per hour.
     * 0 = unlimited (default).
     *
     * <p>Implies minimum delay: {@code 3600000 / maxRequestsPerHour} ms
     *
     * @return the maximum RPH, or 0 for unlimited
     */
    double maxRequestsPerHour() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY CONSTRAINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum number of concurrent sample executions.
     * 0 = sequential execution (default, current behavior).
     * 1 = sequential execution (explicit).
     * N &gt; 1 = up to N samples execute in parallel.
     *
     * <p>Note: Concurrency interacts with rate constraints. If concurrency
     * would cause rate limits to be exceeded, the framework automatically
     * throttles.
     *
     * @return the maximum concurrent requests, or 0/1 for sequential
     */
    int maxConcurrentRequests() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECT DELAY CONSTRAINT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum delay between sample executions in milliseconds.
     * 0 = no delay (default).
     *
     * <p>This is the simplest form of pacing—a direct delay specification
     * without requiring the developer to think in terms of rates.
     *
     * <p>When combined with rate-based constraints, the most restrictive
     * constraint wins (highest delay).
     *
     * @return the minimum delay in milliseconds, or 0 for no delay
     */
    long minMsPerSample() default 0;
}

