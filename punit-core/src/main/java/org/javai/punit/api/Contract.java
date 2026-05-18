package org.javai.punit.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.outcome.Outcome.Fail;
import org.javai.outcome.Outcome.Ok;
import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.criterion.Criterion;
import org.javai.punit.api.criterion.CriterionSampleResult;
import org.javai.punit.api.criterion.DefaultCriterion;

/**
 * The operational layer of a service contract: how to invoke the service for
 * one sample, and what counts as success when the service returns.
 *
 * <p>{@link ServiceContract} extends this interface, so an author writing
 * {@code class MyServiceContract implements ServiceContract<F, I, O>} satisfies both
 * surfaces with one {@code implements} clause. The author overrides
 * exactly two methods:
 *
 * <ul>
 *   <li>{@link #invoke(Object, TokenTracker) invoke} — the service
 *       call. Returns {@link Outcome.Ok} for a successful invocation
 *       or {@link Outcome.Fail} for an expected business-level
 *       failure. Records cost via the supplied
 *       {@link TokenTracker}.</li>
 *   <li>{@link #postconditions(PostconditionBuilder) postconditions} —
 *       declares the contract's clauses by populating the supplied
 *       builder with {@code ensure(...)} calls.</li>
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
     * <h4>How the framework treats the return value</h4>
     *
     * <ul>
     *   <li>{@link Outcome.Ok} — the framework treats the sample as a
     *       candidate for postcondition evaluation. The carried value is
     *       what each {@code ensure} clause sees.</li>
     *   <li>{@link Outcome.Fail} — the framework treats the sample as an
     *       apply-level failure. Postconditions are not evaluated (no
     *       result was produced). The full {@link org.javai.outcome.Failure}
     *       — symbolic name, message, optional cause — is preserved on
     *       the {@link ServiceContractOutcome} for diagnostics.</li>
     * </ul>
     *
     * <h4>How the framework treats a thrown exception</h4>
     *
     * <p>The framework does not catch exceptions thrown from this
     * method. A thrown exception propagates out of the sample runner,
     * out of the engine, and aborts the run. How an author handles
     * exception-prone code inside this method body is their decision;
     * the framework neither encourages nor discourages any particular
     * pattern.
     *
     * <h4>Cost tracking</h4>
     *
     * <p>Call {@code tracker.recordTokens(n)} to record cost incurred
     * during this invocation. The framework derives the per-sample
     * token count by diffing {@link TokenTracker#totalTokens()} before
     * and after the call. Authors with no cost to track simply omit
     * the call.
     *
     * @param input   the per-sample input
     * @param tracker the per-run cost channel
     * @return the wrapped outcome
     */
    Outcome<O> invoke(I input, TokenTracker tracker);

    /**
     * Declare this contract's postcondition clauses by calling
     * {@link PostconditionBuilder#ensure ensure} on the supplied builder.
     *
     * <p>The framework constructs a fresh builder, calls this method
     * to populate it, and reads the resulting clause list out via the
     * no-arg {@link #postconditions()} accessor.
     *
     * <p>The default body is empty — appropriate for contracts that
     * partition their acceptance into multiple criteria via
     * {@link #criteria()} (the clauses live on the individual
     * criteria rather than at the contract level), and also for
     * smoke-test scaffolding or throwaway fixtures that genuinely
     * have no acceptance criteria. Contracts that declare
     * postconditions at the contract level override this method.
     *
     * @param b the builder to populate
     */
    default void postconditions(PostconditionBuilder<O> b) {
        // No-op default. Override to declare contract-level
        // postconditions; leave defaulted when the contract uses
        // criteria() instead.
    }

    /**
     * Resolves the contract's clauses to an immutable list. The
     * framework hook; do not override. The default implementation
     * builds a fresh {@link PostconditionBuilder}, calls
     * {@link #postconditions(PostconditionBuilder)} to populate it, and
     * returns the built list.
     */
    default List<Postcondition<O>> postconditions() {
        PostconditionBuilder<O> b = new PostconditionBuilder<>();
        postconditions(b);
        return b.build();
    }

    /**
     * Author-facing, value-form criteria declaration — returns a
     * {@link Criteria} value describing the contract's
     * verdict-producing strategy. The intended idiom:
     *
     * <pre>{@code
     * import static org.javai.punit.api.criterion.Posture.*;
     * import static org.javai.punit.api.criterion.Composite.*;
     *
     * @Override public Criteria<Receipt> criteria() {
     *     return meeting(0.9999, SLA)
     *             .contractRef("Payment Provider SLA v2.3, §4.1");
     * }
     * }</pre>
     *
     * <p>The default returns {@link Criteria#empty()}: no explicit
     * declaration. The framework then synthesises a single criterion
     * from the contract's {@link #postconditions()} chain.
     */
    default Criteria<O> criteria() {
        return Criteria.empty();
    }

    /**
     * Resolves the contract's criteria to an immutable runtime
     * list. The framework hook; do not override.
     *
     * <p>If {@link #criteria()} returns a non-empty {@link Criteria},
     * its {@link Criteria#asList() asList()} is the result. Otherwise
     * the K=1 default derived from {@link #postconditions()} is
     * returned.
     *
     * @throws IllegalStateException if the contract declares both a
     *         non-empty postcondition chain (non-empty
     *         {@code postconditions(PostconditionBuilder)} override)
     *         and an explicit non-empty {@code criteria()} value.
     */
    default List<Criterion<O>> effectiveCriteria() {
        Criteria<O> declared = criteria();
        if (!declared.isEmpty()) {
            if (!postconditions().isEmpty()) {
                throw new IllegalStateException(
                        "Contract " + getClass().getName()
                                + " declares both a non-empty postcondition chain"
                                + " (via postconditions(PostconditionBuilder)) and an"
                                + " explicit criteria value (via Criteria<O>"
                                + " criteria()). Move the postconditions onto a"
                                + " criterion via .where(name, predicate), or"
                                + " remove the postconditions override.");
            }
            return declared.asList();
        }
        return List.of(new DefaultCriterion<>(this));
    }

    /**
     * Run one sample without an expected value or a matcher.
     * Postconditions are evaluated against the produced value; no
     * match step.
     *
     * <p>Concrete default; the framework dispatches to this form when
     * the test/experiment hasn't configured matching.
     */
    default ServiceContractOutcome<I, O> apply(I input, TokenTracker tracker) {
        return runSample(input, tracker, Optional.empty());
    }

    /**
     * Run one sample with an expected value, using the equality
     * matcher ({@link ValueMatcher#equality()}) to compare produced
     * vs expected. Concrete default; the framework dispatches to this
     * form when the test/experiment configured an expected list but
     * no custom matcher.
     */
    default ServiceContractOutcome<I, O> apply(I input, O expected, TokenTracker tracker) {
        return apply(input, expected, ValueMatcher.equality(), tracker);
    }

    /**
     * Run one sample with an expected value and a custom matcher.
     * Concrete default; the framework dispatches to this form when
     * the test/experiment configured matching with a custom matcher.
     */
    default ServiceContractOutcome<I, O> apply(I input, O expected, ValueMatcher<O> matcher, TokenTracker tracker) {
        return runSample(input, tracker,
                Optional.of(value -> matcher.match(expected, value)));
    }

    private ServiceContractOutcome<I, O> runSample(
            I input,
            TokenTracker tracker,
            Optional<Function<O, MatchResult>> matchStep) {

        long startTokens = tracker.totalTokens();
        long start = System.nanoTime();
        Outcome<O> result = invoke(input, tracker);
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        long sampleTokens = Math.max(0L, tracker.totalTokens() - startTokens);

        ClauseEvaluation clauseEvaluation = result instanceof Ok<O> ok
                ? evaluateClauses(ok.value())
                : ClauseEvaluation.empty();   // postcondition evaluation skipped on apply-level failure

        Optional<MatchResult> match = result instanceof Ok<O> ok2
                ? matchStep.map(step -> step.apply(ok2.value()))
                : Optional.empty();

        return new ServiceContractOutcome<>(
                result,
                this,
                clauseEvaluation.postconditionResults(),
                match,
                sampleTokens,
                duration,
                clauseEvaluation.criterionSampleResults());
    }

    private ClauseEvaluation evaluateClauses(O value) {
        // Walk criteria via per-sample evaluate(value). For each criterion
        // we get a three-valued CriterionSampleResult; the verdict path
        // (which consumes a flat List<PostconditionResult>) is fed the
        // per-postcondition results on PASS / FAIL, and a single synthetic
        // failed PostconditionResult on INCONCLUSIVE — preserving the
        // reason's name and message for diagnostics. The
        // synthetic-result-on-INCONCLUSIVE mapping is the step-2 default
        // for the denominator policy; a configurable policy is the subject
        // of a later step. The per-criterion sample results are retained
        // verbatim alongside the flat list so downstream consumers — the
        // per-criterion accumulator, the per-criterion verdict computation
        // — can read the methodology-level partition unit's behaviour
        // without re-walking the criteria.
        List<PostconditionResult> flat = new ArrayList<>();
        List<CriterionSampleResult> perCriterion = new ArrayList<>();
        for (Criterion<O> criterion : effectiveCriteria()) {
            CriterionSampleResult result = criterion.evaluate(value);
            perCriterion.add(result);
            switch (result.outcome()) {
                case PASS, FAIL -> flat.addAll(result.postconditionResults());
                case INCONCLUSIVE -> {
                    Outcome.Fail<?> reason = result.reason().orElseThrow();
                    flat.add(PostconditionResult.failed(
                            criterion.id(),
                            reason));
                }
            }
        }
        return new ClauseEvaluation(flat, perCriterion);
    }

    record ClauseEvaluation(
            List<PostconditionResult> postconditionResults,
            List<CriterionSampleResult> criterionSampleResults) {
        static ClauseEvaluation empty() {
            return new ClauseEvaluation(List.of(), List.of());
        }
    }
}
