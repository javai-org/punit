package org.javai.punit.api.typed;

import java.util.List;
import java.util.Objects;

import org.javai.outcome.Outcome;

/**
 * Punit's per-sample outcome record. Wraps the raw result of a use
 * case invocation together with the {@link PostconditionEvaluator}
 * that classifies it against the service contract, and a first-class
 * {@code tokens} count reporting the resource cost the invocation
 * consumed.
 *
 * <h2>Lazy postcondition evaluation is deliberate</h2>
 *
 * <p>Postcondition evaluation is lazy — the record stores the
 * <em>evaluator</em>, not the evaluated results. {@link #value()} and
 * {@link #evaluatePostconditions()} re-run the evaluator every call.
 * This matches the contract of the legacy
 * {@code org.javai.punit.contract.UseCaseOutcome} and exists so that:
 *
 * <ul>
 *   <li>the typed engine pays the evaluation cost once per sample
 *       (via a cached count in {@code SampleSummary.from(...)}), but</li>
 *   <li>downstream consumers — reporters, verdict sinks,
 *       Sentinel — can choose independently whether to enumerate
 *       postconditions for diagnostic output;</li>
 *   <li>authors whose postconditions involve expensive checks (a
 *       semantic-similarity call, a JSON parse, etc.) can memoise
 *       inside their own evaluator if they wish, without the outcome
 *       type dictating a caching policy.</li>
 * </ul>
 *
 * <h2>Success / failure classification</h2>
 *
 * <p>{@link #value()} derives the overall {@link Outcome} from the
 * postcondition results: all postconditions passing ⇒
 * {@link Outcome#ok(Object)} wrapping the raw result; any failing ⇒
 * {@link Outcome#fail(String, String)} carrying the first failing
 * postcondition's description + reason. Callers who want the full
 * list call {@link #evaluatePostconditions()} instead.
 *
 * <h2>Tokens as a first-class concept</h2>
 *
 * <p>The {@code tokens} field is the resource cost this invocation
 * consumed — tokens for LLM calls, request units for API calls, or
 * whatever the use case's domain measures. It is a first-class
 * record component rather than a metadata-map entry because the
 * framework's budget requirements (RC02 per-method token budget,
 * RC03 static charge, RC04 dynamic recording) depend on it
 * throughout the pipeline. Use cases that don't track a cost leave
 * it at zero.
 *
 * @param rawResult the raw value returned by the service (may be
 *                  {@code null} when the invocation has no value to
 *                  carry, e.g. for {@link #fail(String, String) fail}
 *                  outcomes)
 * @param postconditions the evaluator — must be non-null; use
 *                       {@link PostconditionEvaluator#trivial()} when
 *                       there is no contract
 * @param tokens the token cost consumed by this invocation; {@code 0}
 *               when the use case does not report a cost
 * @param <OT> the raw result type
 */
public record UseCaseOutcome<OT>(
        OT rawResult,
        PostconditionEvaluator<OT> postconditions,
        long tokens) {

    public UseCaseOutcome {
        Objects.requireNonNull(postconditions, "postconditions");
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be non-negative, got " + tokens);
        }
    }

    /**
     * Derives the overall {@link Outcome} by evaluating every
     * postcondition against the raw result. Recomputes on every call
     * (see class javadoc on laziness).
     *
     * @return {@link Outcome.Ok} wrapping the raw result if all
     *         postconditions pass, otherwise {@link Outcome.Fail}
     *         carrying the first failing postcondition's description
     *         and reason
     */
    public Outcome<OT> value() {
        List<PostconditionResult> results = postconditions.evaluate(rawResult);
        for (PostconditionResult r : results) {
            if (r.failed()) {
                return Outcome.fail(r.description(),
                        r.failureReason().orElse("postcondition failed"));
            }
        }
        return Outcome.ok(rawResult);
    }

    /**
     * The full list of postcondition results for this outcome.
     * Re-evaluates on every call; memoise inside your evaluator if
     * the check cost is not negligible.
     */
    public List<PostconditionResult> evaluatePostconditions() {
        return postconditions.evaluate(rawResult);
    }

    /**
     * Returns a copy of this outcome with {@code tokens} set to
     * {@code n}. Useful at the end of an apply() implementation:
     * {@snippet :
     *   return UseCaseOutcome.ok(result).withTokens(llm.tokensUsed());
     * }
     */
    public UseCaseOutcome<OT> withTokens(long n) {
        return new UseCaseOutcome<>(rawResult, postconditions, n);
    }

    // ── Contract-free convenience factories ──────────────────────────

    /**
     * Build a success outcome with no postconditions and zero tokens.
     * Equivalent to the use case declaring "I produced this value; I
     * have no contract to enforce; this invocation consumed no
     * measurable cost."
     *
     * @param value the successful value; may be {@code null}
     */
    public static <OT> UseCaseOutcome<OT> ok(OT value) {
        return new UseCaseOutcome<>(value, PostconditionEvaluator.trivial(), 0L);
    }

    /**
     * Build a failure outcome with no raw result and zero tokens,
     * carrying a single always-failing postcondition.
     *
     * @param name short failure-code name (e.g. {@code "validation_failed"})
     * @param message human-readable description
     */
    public static <OT> UseCaseOutcome<OT> fail(String name, String message) {
        return new UseCaseOutcome<>(null, PostconditionEvaluator.alwaysFailing(name, message), 0L);
    }

    /**
     * Build an outcome directly from a raw result and a
     * {@link PostconditionEvaluator}, with zero tokens. The
     * contract-aware builder used by use cases that declare a
     * {@code ServiceContract} will call this (or the canonical
     * constructor) after {@code execute()} captures the result, then
     * attach tokens with {@link #withTokens(long)}.
     */
    public static <OT> UseCaseOutcome<OT> of(OT rawResult, PostconditionEvaluator<OT> postconditions) {
        return new UseCaseOutcome<>(rawResult, postconditions, 0L);
    }
}
