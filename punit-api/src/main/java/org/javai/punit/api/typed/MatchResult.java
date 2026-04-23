package org.javai.punit.api.typed;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of comparing an expected value against an actual value —
 * the instance-conformance signal for a single sample. Produced by a
 * {@link ValueMatcher} and attached to a {@link UseCaseOutcome} via
 * {@link UseCaseOutcome#withMatch(MatchResult)}.
 *
 * <p>{@code expected} and {@code actual} are carried as {@code Object}
 * rather than a parameterised type so that matchers over projections
 * (e.g. "expected the status field of the response") can be expressed
 * without introducing another type parameter on {@link UseCaseOutcome}.
 * Author discipline covers the typed-match case.
 *
 * @param description human-readable label of what was compared
 *                    (e.g. {@code "translation equals reference"})
 * @param expected the expected value the author declared
 * @param actual the value produced by the use case invocation
 * @param matches {@code true} when the matcher classified the two as
 *                equivalent
 * @param diff optional human-readable description of the difference;
 *             present when {@code matches == false} and the matcher
 *             supplies one, empty otherwise
 */
public record MatchResult(
        String description,
        Object expected,
        Object actual,
        boolean matches,
        Optional<String> diff) {

    public MatchResult {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(diff, "diff");
    }

    public static MatchResult pass(String description, Object expected, Object actual) {
        return new MatchResult(description, expected, actual, true, Optional.empty());
    }

    public static MatchResult fail(String description, Object expected, Object actual, String diff) {
        Objects.requireNonNull(diff, "diff");
        return new MatchResult(description, expected, actual, false, Optional.of(diff));
    }
}
