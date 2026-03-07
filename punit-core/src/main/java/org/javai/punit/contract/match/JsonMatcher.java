package org.javai.punit.contract.match;

/**
 * JSON semantic comparison matcher.
 *
 * <p>JsonMatcher compares JSON strings semantically rather than textually,
 * properly handling whitespace differences and property ordering.
 *
 * <h2>Dependency Requirement</h2>
 * <p>This matcher requires the zjsonpatch library at runtime:
 * <pre>{@code
 * // In build.gradle.kts
 * implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
 * }</pre>
 *
 * <p>If zjsonpatch is not available, calling {@link #create()} will throw an
 * {@link UnsupportedOperationException}. Use {@link #isAvailable()} to check
 * availability before creating a matcher.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Check availability first
 * if (JsonMatcher.isAvailable()) {
 *     outcome.expecting("{\"name\":\"Alice\"}", ResultExtractor.identity(), JsonMatcher.create());
 * }
 * }</pre>
 *
 * <h2>Diff Output</h2>
 * <p>When values don't match, the diff describes the differences using RFC 6902 JSON Patch
 * operations (add, remove, replace, move, copy).
 *
 * @see VerificationMatcher
 */
public final class JsonMatcher implements VerificationMatcher<String> {

    private static final Boolean AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("com.flipkart.zjsonpatch.JsonDiff");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        AVAILABLE = available;
    }

    private JsonMatcher() {
        // Private constructor - use create()
    }

    /**
     * Returns whether the zjsonpatch library is available at runtime.
     *
     * @return true if JSON matching is available
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Creates a new JSON matcher.
     *
     * @return a JSON matcher
     * @throws UnsupportedOperationException if zjsonpatch is not on the classpath
     */
    public static JsonMatcher create() {
        if (!AVAILABLE) {
            throw new UnsupportedOperationException(
                    "JsonMatcher requires the zjsonpatch library. " +
                    "Add to your build: implementation(\"com.flipkart.zjsonpatch:zjsonpatch:0.4.16\")"
            );
        }
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

        // Delegate to the isolated class that uses zjsonpatch
        return JsonMatcherDelegate.compare(expected, actual);
    }
}
