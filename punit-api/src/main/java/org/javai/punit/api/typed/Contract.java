package org.javai.punit.api.typed;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.outcome.Outcome.Fail;
import org.javai.outcome.Outcome.Ok;

/**
 * The operational layer of a use case: how to invoke the service for
 * one sample, and what counts as success when the service returns.
 *
 * <p>{@link UseCase} extends this interface, so an author writing
 * {@code class MyUseCase implements UseCase<F, I, O>} satisfies both
 * surfaces with one {@code implements} clause. The author overrides
 * exactly two methods:
 *
 * <ul>
 *   <li>{@link #invoke(Object, TokenTracker) invoke} — the service
 *       call. Returns {@link Outcome.Ok} for a successful invocation
 *       or {@link Outcome.Fail} for an expected business-level
 *       failure. Records cost via the supplied
 *       {@link TokenTracker}.</li>
 *   <li>{@link #postconditions(ContractBuilder) postconditions} —
 *       declares the contract's clauses by populating the supplied
 *       builder with {@code ensure(...)} and {@code deriving(...)}
 *       calls.</li>
 * </ul>
 *
 * <p>The three {@code apply} forms are concrete defaults the
 * framework dispatches to per sample. Authors do not override them.
 * Each one wraps {@code invoke} with timing, cost diff, postcondition
 * evaluation, and (for the matching forms) the comparison of the
 * produced value against an expected value via a
 * {@link ValueMatcher}.
 *
 * @param <I> the per-sample input type
 * @param <O> the per-sample output value type
 */
public interface Contract<I, O> {

    /**
     * Invoke the service for one sample.
     *
     * <p>Return {@link Outcome.Ok} carrying the produced value for a
     * successful invocation, or {@link Outcome.Fail} for an expected
     * business-level failure (a contract violation, a service-returned
     * error code, an explicit refusal). The framework counts
     * {@code Ok} samples as candidates for postcondition evaluation
     * and {@code Fail} samples as apply-level failures, preserving the
     * full {@link org.javai.outcome.Failure} for diagnostics.
     *
     * <p>Throwing from this method is reserved for <em>defects</em> —
     * programming mistakes, misconfiguration, catastrophe. A thrown
     * exception bubbles out and aborts the run. Do not throw to
     * signal a failed sample; return {@code Outcome.fail(name, msg)}.
     *
     * <p>Record cost via {@code tracker.recordTokens(n)}. The
     * framework derives the per-sample token count by diffing
     * {@link TokenTracker#totalTokens()} before and after this call.
     * Authors with no cost to track simply omit the call.
     *
     * @param input   the per-sample input
     * @param tracker the per-run cost channel
     * @return the wrapped outcome
     */
    Outcome<O> invoke(I input, TokenTracker tracker);

    /**
     * Declare this contract's postcondition clauses by calling
     * {@link ContractBuilder#ensure ensure} and
     * {@link ContractBuilder#deriving deriving} on the supplied
     * builder.
     *
     * <p>The framework constructs a fresh builder, calls this method
     * to populate it, and reads the resulting clause list out via the
     * no-arg {@link #postconditions()} accessor.
     *
     * <p>Use cases that genuinely have no acceptance criteria
     * (smoke-test scaffolding, throwaway fixtures) leave the body
     * empty — the explicit empty body marks the choice.
     *
     * @param b the builder to populate
     */
    void postconditions(ContractBuilder<O> b);

    /**
     * Resolves the contract's clauses to an immutable list. The
     * framework hook; do not override. The default implementation
     * builds a fresh {@link ContractBuilder}, calls
     * {@link #postconditions(ContractBuilder)} to populate it, and
     * returns the built list.
     */
    default List<Postcondition<O>> postconditions() {
        ContractBuilder<O> b = new ContractBuilder<>();
        postconditions(b);
        return b.build();
    }

    /**
     * Run one sample without an expected value or a matcher.
     * Postconditions are evaluated against the produced value; no
     * match step.
     *
     * <p>Concrete default; the framework dispatches to this form when
     * the test/experiment hasn't configured matching.
     */
    default UseCaseOutcome<I, O> apply(I input, TokenTracker tracker) {
        return runSample(input, tracker, Optional.empty());
    }

    /**
     * Run one sample with an expected value, using the equality
     * matcher ({@link ValueMatcher#equality()}) to compare produced
     * vs expected. Concrete default; the framework dispatches to this
     * form when the test/experiment configured an expected list but
     * no custom matcher.
     */
    default UseCaseOutcome<I, O> apply(I input, O expected, TokenTracker tracker) {
        return apply(input, expected, ValueMatcher.equality(), tracker);
    }

    /**
     * Run one sample with an expected value and a custom matcher.
     * Concrete default; the framework dispatches to this form when
     * the test/experiment configured matching with a custom matcher.
     */
    default UseCaseOutcome<I, O> apply(I input, O expected, ValueMatcher<O> matcher, TokenTracker tracker) {
        return runSample(input, tracker,
                Optional.of(value -> matcher.match(expected, value)));
    }

    private UseCaseOutcome<I, O> runSample(
            I input,
            TokenTracker tracker,
            Optional<Function<O, MatchResult>> matchStep) {

        long startTokens = tracker.totalTokens();
        long start = System.nanoTime();
        Outcome<O> result = invoke(input, tracker);
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        long sampleTokens = Math.max(0L, tracker.totalTokens() - startTokens);

        List<PostconditionResult> clauseResults = result instanceof Ok<O> ok
                ? evaluateClauses(ok.value())
                : List.of();   // postcondition evaluation skipped on apply-level failure

        Optional<MatchResult> match = result instanceof Ok<O> ok2
                ? matchStep.map(step -> step.apply(ok2.value()))
                : Optional.empty();

        return new UseCaseOutcome<>(
                result,
                this,
                clauseResults,
                match,
                sampleTokens,
                duration);
    }

    private List<PostconditionResult> evaluateClauses(O value) {
        List<PostconditionResult> out = new ArrayList<>();
        for (Postcondition<O> p : postconditions()) {
            out.addAll(p.evaluateAll(value));
        }
        return out;   // UseCaseOutcome canonical constructor defensive-copies
    }
}
