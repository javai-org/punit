package org.javai.punit.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares latency thresholds for a probabilistic test.
 *
 * <p>Used as an attribute of {@link ProbabilisticTest} to specify per-percentile
 * latency constraints. Each percentile threshold is expressed in milliseconds.
 * A value of {@code -1} (default) means "not asserted" — that percentile will
 * not be checked.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ProbabilisticTest(
 *     samples = 100,
 *     minPassRate = 0.95,
 *     latency = @Latency(p95Ms = 500, p99Ms = 1000)
 * )
 * void testServiceLatency() {
 *     Response response = service.call();
 *     assertThat(response.isValid()).isTrue();
 * }
 * }</pre>
 *
 * <p>Latency is measured as the wall-clock time of each successful sample invocation.
 * Only successful samples contribute to latency percentile computation. After all
 * samples complete, observed percentiles are compared against the declared thresholds.
 *
 * <h2>Threshold semantics</h2>
 * <p>The test fails if any observed percentile exceeds its declared threshold:
 * <pre>
 * observed_p95 &lt;= p95Ms   → PASS for p95
 * observed_p95 &gt; p95Ms    → FAIL for p95
 * </pre>
 *
 * @see ProbabilisticTest#latency()
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Latency {

    /**
     * Maximum allowed p50 (median) latency in milliseconds.
     * {@code -1} means this percentile is not asserted.
     *
     * @return the p50 threshold in ms, or -1 if not asserted
     */
    long p50Ms() default -1;

    /**
     * Maximum allowed p90 latency in milliseconds.
     * {@code -1} means this percentile is not asserted.
     *
     * @return the p90 threshold in ms, or -1 if not asserted
     */
    long p90Ms() default -1;

    /**
     * Maximum allowed p95 latency in milliseconds.
     * {@code -1} means this percentile is not asserted.
     *
     * @return the p95 threshold in ms, or -1 if not asserted
     */
    long p95Ms() default -1;

    /**
     * Maximum allowed p99 latency in milliseconds.
     * {@code -1} means this percentile is not asserted.
     *
     * @return the p99 threshold in ms, or -1 if not asserted
     */
    long p99Ms() default -1;

    /**
     * Opts out of latency evaluation entirely (suppresses even advisory warnings).
     *
     * <p>When a baseline spec contains latency data, PUnit automatically derives
     * latency thresholds — no explicit opt-in is needed. By default, latency
     * assertions are <em>advisory</em>: breaches produce warnings in the output
     * but do not fail the test. To make breaches fail the test, set
     * {@code -Dpunit.latency.enforce=true} or {@code PUNIT_LATENCY_ENFORCE=true}.
     *
     * <p>Set {@code disabled = true} to suppress latency evaluation entirely —
     * no warnings, no enforcement, no latency line in the output.
     *
     * <p>Default: {@code false} (latency is evaluated when thresholds are available,
     * either explicit or baseline-derived).
     *
     * @return true to disable latency evaluation entirely
     */
    boolean disabled() default false;
}
