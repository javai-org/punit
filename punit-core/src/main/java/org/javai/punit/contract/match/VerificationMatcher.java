package org.javai.punit.contract.match;

/**
 * Compares an expected value against an actual value and produces a match result.
 *
 * <p>Verification matchers enable instance conformance checking in PUnit experiments,
 * allowing comparison of actual service results against expected values using
 * customizable comparison strategies.
 *
 * <h2>Built-in Matchers</h2>
 * <ul>
 *   <li>{@link StringMatcher} - String comparison with various modes (exact, ignore case, etc.)</li>
 *   <li>{@link JsonMatcher} - JSON semantic comparison (requires zjsonpatch)</li>
 * </ul>
 *
 * <h2>Custom Matchers</h2>
 * <p>Implement this interface to create custom comparison logic:
 * <pre>{@code
 * VerificationMatcher<MyType> matcher = (expected, actual) -> {
 *     if (expected.semanticallyEquals(actual)) {
 *         return MatchResult.match();
 *     }
 *     return MatchResult.mismatch("Expected " + expected + " but got " + actual);
 * };
 * }</pre>
 *
 * @param <T> the type of values being compared
 * @see StringMatcher
 * @see JsonMatcher
 * @see org.javai.punit.contract.UseCaseOutcome.MetadataBuilder#expecting
 */
@FunctionalInterface
public interface VerificationMatcher<T> {

    /**
     * Compares an expected value against an actual value.
     *
     * @param expected the expected value (may be null depending on matcher implementation)
     * @param actual the actual value (may be null depending on matcher implementation)
     * @return the match result indicating success or describing the mismatch
     */
    MatchResult match(T expected, T actual);

    /**
     * The result of a verification match operation.
     *
     * <p>A match result captures whether values matched and, if not, a description
     * of the difference. Use the factory methods {@link #match()} and {@link #mismatch(String)}
     * to create instances.
     *
     * @param matches true if the values matched, false otherwise
     * @param diff description of the difference (empty string for matches)
     */
    record MatchResult(boolean matches, String diff) {

        /**
         * Creates a successful match result.
         *
         * @return a result indicating the values matched
         */
        public static MatchResult match() {
            return new MatchResult(true, "");
        }

        /**
         * Creates a mismatch result with a description of the difference.
         *
         * @param diff a human-readable description of how the values differed
         * @return a result indicating the values did not match
         */
        public static MatchResult mismatch(String diff) {
            return new MatchResult(false, diff);
        }

        /**
         * Returns whether this result represents a mismatch.
         *
         * @return true if the values did not match
         */
        public boolean mismatches() {
            return !matches;
        }
    }
}
