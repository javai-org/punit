package org.javai.punit.contract.match;

import java.util.Objects;

/**
 * String comparison matcher with various comparison modes.
 *
 * <p>StringMatcher provides common string comparison strategies for verifying
 * that actual string results match expected values.
 *
 * <h2>Comparison Modes</h2>
 * <ul>
 *   <li>{@link #exact()} - Exact string equality</li>
 *   <li>{@link #ignoreCase()} - Case-insensitive comparison</li>
 *   <li>{@link #trimWhitespace()} - Ignores leading/trailing whitespace</li>
 *   <li>{@link #normalizeWhitespace()} - Normalizes all whitespace sequences to single spaces</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Exact matching
 * outcome.expecting("Hello", ResultExtractor.identity(), StringMatcher.exact());
 *
 * // Case-insensitive matching
 * outcome.expecting("hello", ResultExtractor.identity(), StringMatcher.ignoreCase());
 *
 * // Flexible whitespace matching
 * outcome.expecting("hello world", ResultExtractor.identity(), StringMatcher.normalizeWhitespace());
 * }</pre>
 *
 * <h2>Null Handling</h2>
 * <ul>
 *   <li>Both null → match</li>
 *   <li>One null → mismatch</li>
 * </ul>
 *
 * @see VerificationMatcher
 */
public final class StringMatcher implements VerificationMatcher<String> {

    private static final int MAX_VALUE_LENGTH = 100;

    private final Mode mode;

    private StringMatcher(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * Returns a matcher that requires exact string equality.
     *
     * @return an exact match string matcher
     */
    public static StringMatcher exact() {
        return new StringMatcher(Mode.EXACT);
    }

    /**
     * Returns a matcher that ignores case differences.
     *
     * @return a case-insensitive string matcher
     */
    public static StringMatcher ignoreCase() {
        return new StringMatcher(Mode.IGNORE_CASE);
    }

    /**
     * Returns a matcher that trims leading and trailing whitespace before comparing.
     *
     * @return a whitespace-trimming string matcher
     */
    public static StringMatcher trimWhitespace() {
        return new StringMatcher(Mode.TRIM_WHITESPACE);
    }

    /**
     * Returns a matcher that normalizes all whitespace sequences to single spaces.
     *
     * <p>This mode:
     * <ul>
     *   <li>Trims leading and trailing whitespace</li>
     *   <li>Replaces all whitespace sequences (spaces, tabs, newlines) with single spaces</li>
     * </ul>
     *
     * @return a whitespace-normalizing string matcher
     */
    public static StringMatcher normalizeWhitespace() {
        return new StringMatcher(Mode.NORMALIZE_WHITESPACE);
    }

    @Override
    public MatchResult match(String expected, String actual) {
        // Handle null cases
        if (expected == null && actual == null) {
            return MatchResult.match();
        }
        if (expected == null) {
            return MatchResult.mismatch("expected null but got: " + truncate(actual));
        }
        if (actual == null) {
            return MatchResult.mismatch("expected: " + truncate(expected) + " but got null");
        }

        // Apply mode-specific transformation and comparison
        String transformedExpected = transform(expected);
        String transformedActual = transform(actual);

        boolean matches = switch (mode) {
            case EXACT, TRIM_WHITESPACE, NORMALIZE_WHITESPACE -> transformedExpected.equals(transformedActual);
            case IGNORE_CASE -> transformedExpected.equalsIgnoreCase(transformedActual);
        };

        if (matches) {
            return MatchResult.match();
        }

        return MatchResult.mismatch("expected: " + truncate(expected) + " but got: " + truncate(actual));
    }

    private String transform(String value) {
        return switch (mode) {
            case EXACT, IGNORE_CASE -> value;
            case TRIM_WHITESPACE -> value.strip();
            case NORMALIZE_WHITESPACE -> value.strip().replaceAll("\\s+", " ");
        };
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_VALUE_LENGTH) {
            return "\"" + value + "\"";
        }
        return "\"" + value.substring(0, MAX_VALUE_LENGTH) + "...\" (" + value.length() + " chars)";
    }

    private enum Mode {
        EXACT,
        IGNORE_CASE,
        TRIM_WHITESPACE,
        NORMALIZE_WHITESPACE
    }
}
