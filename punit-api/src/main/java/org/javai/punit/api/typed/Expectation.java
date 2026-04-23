package org.javai.punit.api.typed;

import java.util.Objects;

/**
 * An input paired with its expected value — the per-sample unit of an
 * instance-conformance experiment or test.
 *
 * <p>When a spec is built via {@code .expectations(Expectation...)}
 * rather than {@code .inputs(IT...)}, the engine walks the pairs
 * round-robin, invokes the use case on each {@link #input}, and
 * compares the produced value against {@link #expected} using the
 * spec's configured {@link ValueMatcher}. The resulting
 * {@link MatchResult} is attached to the
 * {@link UseCaseOutcome} before it reaches the observer, so a failed
 * match counts as a sample failure in the usual way.
 *
 * @param input the input to hand to the use case
 * @param expected the expected value to compare the use case's output
 *                 against
 * @param <IT> the input type
 * @param <OT> the expected (and output) type
 */
public record Expectation<IT, OT>(IT input, OT expected) {

    public Expectation {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(expected, "expected");
    }

    public static <IT, OT> Expectation<IT, OT> of(IT input, OT expected) {
        return new Expectation<>(input, expected);
    }
}
