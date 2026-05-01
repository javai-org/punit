package org.javai.punit.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;

/**
 * The artefact assembled by {@link Contract#apply(Object, TokenTracker)}
 * for one sample. Self-sufficient — every recipient (probabilistic test
 * asserter, baseline writer, optimize meta-prompt builder, explore diff
 * renderer) consumes it without reaching back to the use case for
 * additional state.
 *
 * <h2>Author-side note</h2>
 *
 * <p>Authors do not construct {@code UseCaseOutcome} directly. They
 * implement {@link Contract#invoke(Object, TokenTracker) invoke}
 * returning {@link Outcome.Ok} or {@link Outcome.Fail}; the framework's
 * {@code apply} default wraps that with timing, cost diff, postcondition
 * evaluation, and (for matching forms) the comparison step, then
 * assembles this record.
 *
 * <h2>What it carries</h2>
 *
 * <ul>
 *   <li>{@link #result} — the outcome {@code invoke} returned: an
 *       {@link Outcome.Ok} wrapping the produced value or an
 *       {@link Outcome.Fail} describing an apply-level failure
 *       (transport error, refusal, no value).</li>
 *   <li>{@link #contract} — the {@link Contract} instance that judged
 *       this sample. Recipients consult it for clause descriptions
 *       and metadata; postcondition evaluation has already run, so
 *       the consumer needn't re-evaluate.</li>
 *   <li>{@link #postconditionResults} — the per-clause results of
 *       evaluating the contract against the produced value. Empty
 *       when {@code result} is {@link Outcome.Fail} (no value to
 *       evaluate).</li>
 *   <li>{@link #match} — the optional instance-conformance match
 *       result, present when the test/experiment configured an
 *       expected value via {@link Sampling.Builder#matching}.</li>
 *   <li>{@link #tokens} — the per-sample cost recorded via
 *       {@link TokenTracker} during {@code invoke}.</li>
 *   <li>{@link #duration} — wall-clock time taken by
 *       {@code invoke}.</li>
 * </ul>
 *
 * @param <I> the per-sample input type (binds {@link Contract}'s
 *            input parameter so {@code Contract<I, O>} can be carried)
 * @param <O> the per-sample output value type
 */
public record UseCaseOutcome<I, O>(
        Outcome<O> result,
        Contract<I, O> contract,
        List<PostconditionResult> postconditionResults,
        Optional<MatchResult> match,
        long tokens,
        Duration duration) {

    public UseCaseOutcome {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(postconditionResults, "postconditionResults");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(duration, "duration");
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be non-negative, got " + tokens);
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must be non-negative, got " + duration);
        }
        postconditionResults = List.copyOf(postconditionResults);
    }

    /**
     * Derives the overall sample {@link Outcome} from the result, the
     * match, and the postcondition results. Precedence:
     *
     * <ol>
     *   <li>If {@link #result} is an {@link Outcome.Fail}, returns it
     *       — apply-level failure short-circuits everything.</li>
     *   <li>Else if {@link #match} is present and reports a mismatch,
     *       returns {@code Outcome.fail("instance_conformance", …)}.</li>
     *   <li>Else if any entry in {@link #postconditionResults} failed,
     *       returns {@code Outcome.fail(description, reason)} carrying
     *       the first failure.</li>
     *   <li>Else returns {@link Outcome.Ok} wrapping the produced
     *       value.</li>
     * </ol>
     *
     * <p>This is a derivation, not a stored field — the data lives on
     * {@link #result}, {@link #match}, and {@link #postconditionResults}.
     */
    public Outcome<O> value() {
        if (result instanceof Outcome.Fail<O> f) {
            return f;
        }
        if (match.isPresent() && !match.get().matches()) {
            MatchResult m = match.get();
            return Outcome.fail(
                    "instance_conformance",
                    m.diff().orElse(m.description()));
        }
        for (PostconditionResult r : postconditionResults) {
            if (r.failed()) {
                return Outcome.fail(r.description(),
                        r.failureReason().orElse("postcondition failed"));
            }
        }
        return result;   // an Outcome.Ok
    }
}
