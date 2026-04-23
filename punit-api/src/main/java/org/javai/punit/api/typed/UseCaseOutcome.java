package org.javai.punit.api.typed;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;

/**
 * Punit's per-sample outcome record. Wraps the raw result of a use
 * case invocation together with the {@link PostconditionEvaluator}
 * that classifies it against the service contract, the optional
 * {@link MatchResult} from an instance-conformance check, and a
 * first-class {@code tokens} count reporting the resource cost the
 * invocation consumed.
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
 * <p>{@link #value()} derives the overall {@link Outcome}:
 * <ul>
 *   <li>If {@link #match} is present and
 *       {@link MatchResult#matches() matches()} is false, the outcome
 *       is {@link Outcome.Fail} with the match's diff as the reason —
 *       instance-conformance mismatch counts as a sample failure.</li>
 *   <li>Otherwise the postconditions are evaluated: all passing ⇒
 *       {@link Outcome#ok(Object)} wrapping the raw result; any
 *       failing ⇒ {@link Outcome.Fail} carrying the first failing
 *       postcondition's description and reason.</li>
 * </ul>
 *
 * <h2>Tokens as a first-class concept</h2>
 *
 * <p>The {@code tokens} field is the resource cost this invocation
 * consumed — tokens for LLM calls, request units for API calls, or
 * whatever the use case's domain measures. It is a first-class
 * record component rather than a metadata-map entry because the
 * framework's token-budget enforcement — the static charge declared
 * at spec time and the per-sample tally recorded here — depends on it
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
 * @param match instance-conformance match result; present when the
 *              engine (or use case) ran a {@link ValueMatcher},
 *              {@link Optional#empty()} otherwise
 * @param tokens the token cost consumed by this invocation; {@code 0}
 *               when the use case does not report a cost
 * @param <OT> the raw result type
 */
public record UseCaseOutcome<OT>(
        OT rawResult,
        PostconditionEvaluator<OT> postconditions,
        Optional<MatchResult> match,
        long tokens) {

    public UseCaseOutcome {
        Objects.requireNonNull(postconditions, "postconditions");
        Objects.requireNonNull(match, "match");
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be non-negative, got " + tokens);
        }
    }

    /**
     * Derives the overall {@link Outcome} from match + postconditions.
     * See class javadoc for the precedence rules.
     */
    public Outcome<OT> value() {
        if (match.isPresent() && !match.get().matches()) {
            MatchResult m = match.get();
            return Outcome.fail(
                    "instance_conformance",
                    m.diff().orElse(m.description()));
        }
        List<PostconditionResult> results = postconditions.evaluate(rawResult);
        for (PostconditionResult r : results) {
            if (r.failed()) {
                return Outcome.fail(r.description(),
                        r.failureReason().orElse("postcondition failed"));
            }
        }
        return Outcome.ok(rawResult);
    }

    /** Full list of postcondition results. Re-evaluates on every call. */
    public List<PostconditionResult> evaluatePostconditions() {
        return postconditions.evaluate(rawResult);
    }

    /**
     * Returns a copy of this outcome with {@code tokens} set to
     * {@code n}.
     */
    public UseCaseOutcome<OT> withTokens(long n) {
        return new UseCaseOutcome<>(rawResult, postconditions, match, n);
    }

    /**
     * Returns a copy of this outcome with a {@link MatchResult}
     * attached. The engine calls this after running the spec's
     * configured matcher against the expected value supplied for the
     * sample; authors who perform comparison inside {@code apply}
     * call it directly.
     */
    public UseCaseOutcome<OT> withMatch(MatchResult matchResult) {
        Objects.requireNonNull(matchResult, "matchResult");
        return new UseCaseOutcome<>(rawResult, postconditions, Optional.of(matchResult), tokens);
    }

    // ── Contract-free convenience factories ──────────────────────────

    /**
     * Build a success outcome with no postconditions, no match, and
     * zero tokens.
     *
     * @param value the successful value; may be {@code null}
     */
    public static <OT> UseCaseOutcome<OT> ok(OT value) {
        return new UseCaseOutcome<>(value, PostconditionEvaluator.trivial(), Optional.empty(), 0L);
    }

    /**
     * Build a failure outcome with no raw result, no match, and zero
     * tokens, carrying a single always-failing postcondition.
     */
    public static <OT> UseCaseOutcome<OT> fail(String name, String message) {
        return new UseCaseOutcome<>(null, PostconditionEvaluator.alwaysFailing(name, message),
                Optional.empty(), 0L);
    }

    /**
     * Build an outcome from a raw result and a
     * {@link PostconditionEvaluator}. Match defaults to empty, tokens
     * to zero; use {@link #withMatch(MatchResult)} and
     * {@link #withTokens(long)} to attach.
     */
    public static <OT> UseCaseOutcome<OT> of(OT rawResult, PostconditionEvaluator<OT> postconditions) {
        return new UseCaseOutcome<>(rawResult, postconditions, Optional.empty(), 0L);
    }
}
