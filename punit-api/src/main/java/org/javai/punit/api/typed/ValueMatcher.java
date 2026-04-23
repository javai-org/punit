package org.javai.punit.api.typed;

import java.util.Objects;

/**
 * Compares an expected value against an actual value, producing a
 * {@link MatchResult} rich enough to report the outcome in both
 * diagnostic and aggregate form — the instance-conformance channel
 * of an experiment or probabilistic test.
 *
 * <p>Authors who need a richer comparison than equality supply a
 * custom matcher via {@code spec.matcher(...)}. The built-in
 * {@link #equality()} treats {@link Objects#equals(Object, Object)}
 * as the comparison.
 *
 * @param <T> the type being compared (typically the use case's output
 *            type or a projection of it)
 */
@FunctionalInterface
public interface ValueMatcher<T> {

    MatchResult match(T expected, T actual);

    /**
     * The default matcher — equality by
     * {@link Objects#equals(Object, Object)}. Good enough for scalar
     * comparisons and exact-match string tests; richer matchers
     * (string whitespace-insensitive, JSON structural, regex) are the
     * author's responsibility until a shared matcher library lands.
     */
    static <T> ValueMatcher<T> equality() {
        return (expected, actual) ->
                Objects.equals(expected, actual)
                        ? MatchResult.pass("value equals expected", expected, actual)
                        : MatchResult.fail("value equals expected", expected, actual,
                                "expected " + expected + " but got " + actual);
    }
}
