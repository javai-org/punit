package org.javai.punit.contract;

/**
 * Thread-scoped tracker for which dimensions of a {@link UseCaseOutcome} were asserted
 * during a single sample execution.
 *
 * <p>The probabilistic test framework uses this to determine which dimensions
 * (functional contract, latency) were exercised by the test method, enabling
 * per-dimension verdict rendering and statistical tracking.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #begin()} — called before each sample execution</li>
 *   <li>Test method calls assertion methods on {@code UseCaseOutcome}, which record dimensions here</li>
 *   <li>Framework reads asserted dimensions via {@link #current()}</li>
 *   <li>{@link #end()} — called after each sample execution (in a finally block)</li>
 * </ol>
 *
 * <p>This class is not thread-safe across threads, but each thread has its own instance
 * via {@link ThreadLocal}.
 */
public final class AssertionScope {

    private static final ThreadLocal<AssertionScope> CURRENT = new ThreadLocal<>();

    private boolean functionalAsserted;
    private boolean latencyAsserted;
    private boolean functionalPassed;
    private boolean latencyPassed;

    private AssertionScope() {
    }

    /**
     * Begins a new assertion scope for the current sample.
     * Must be paired with {@link #end()} in a finally block.
     */
    public static void begin() {
        CURRENT.set(new AssertionScope());
    }

    /**
     * Returns the current assertion scope, or {@code null} if no scope is active.
     *
     * @return the current scope, or null
     */
    public static AssertionScope current() {
        return CURRENT.get();
    }

    /**
     * Ends the current assertion scope, releasing the thread-local.
     */
    public static void end() {
        CURRENT.remove();
    }

    /**
     * Records that the functional dimension (postconditions and expected value matching)
     * was asserted with the given result.
     *
     * @param passed true if all functional criteria passed
     */
    void recordFunctional(boolean passed) {
        this.functionalAsserted = true;
        this.functionalPassed = passed;
    }

    /**
     * Records that the latency dimension (duration constraint) was asserted
     * with the given result.
     *
     * @param passed true if the duration constraint was satisfied
     */
    void recordLatency(boolean passed) {
        this.latencyAsserted = true;
        this.latencyPassed = passed;
    }

    /**
     * Returns whether the functional dimension was asserted in this scope.
     */
    public boolean isFunctionalAsserted() {
        return functionalAsserted;
    }

    /**
     * Returns whether the latency dimension was asserted in this scope.
     */
    public boolean isLatencyAsserted() {
        return latencyAsserted;
    }

    /**
     * Returns whether the functional assertion passed.
     * Only meaningful if {@link #isFunctionalAsserted()} is true.
     */
    public boolean isFunctionalPassed() {
        return functionalPassed;
    }

    /**
     * Returns whether the latency assertion passed.
     * Only meaningful if {@link #isLatencyAsserted()} is true.
     */
    public boolean isLatencyPassed() {
        return latencyPassed;
    }
}
