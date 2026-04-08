package org.javai.punit.contract.match;

/**
 * JSON semantic comparison matcher.
 *
 * <p>JsonMatcher compares JSON strings semantically rather than textually,
 * properly handling whitespace differences and property ordering.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * outcome.expecting("{\"name\":\"Alice\"}", ResultExtractor.identity(), JsonMatcher.create());
 * }</pre>
 *
 * <h2>Diff Output</h2>
 * <p>When values don't match, the diff describes the differences using
 * add, remove, and replace operations with JSON Pointer paths.
 *
 * @see VerificationMatcher
 */
public final class JsonMatcher implements VerificationMatcher<String> {

    private JsonMatcher() {
        // Private constructor - use create()
    }

    /**
     * Returns whether JSON matching is available.
     *
     * <p>This method always returns {@code true}. It is retained for
     * backwards compatibility with code that checked availability when
     * JSON matching required an optional dependency.
     *
     * @return always {@code true}
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * Creates a new JSON matcher.
     *
     * @return a JSON matcher
     */
    public static JsonMatcher create() {
        return new JsonMatcher();
    }

    @Override
    public MatchResult match(String expected, String actual) {
        // Handle null cases
        if (expected == null && actual == null) {
            return MatchResult.match();
        }
        if (expected == null) {
            return MatchResult.mismatch("expected null but got JSON");
        }
        if (actual == null) {
            return MatchResult.mismatch("expected JSON but got null");
        }

        // Delegate to the isolated class that uses Jackson
        return JsonMatcherDelegate.compare(expected, actual);
    }
}
