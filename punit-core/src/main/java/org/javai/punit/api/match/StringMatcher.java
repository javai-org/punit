package org.javai.punit.api.match;

import java.util.Objects;

import org.javai.punit.api.MatchResult;
import org.javai.punit.api.ValueMatcher;

/**
 * String comparison matcher with four comparison modes.
 *
 * <p>The four modes are selected via factory methods:
 *
 * <ul>
 *   <li>{@link #exact()} — character-by-character equality.</li>
 *   <li>{@link #ignoreCase()} — case-insensitive comparison.</li>
 *   <li>{@link #trimWhitespace()} — strips leading and trailing
 *       whitespace before comparing.</li>
 *   <li>{@link #normalizeWhitespace()} — strips leading and trailing
 *       whitespace and collapses internal whitespace runs to single
 *       spaces before comparing.</li>
 * </ul>
 *
 * <p>Null handling: both null matches; one null mismatches. Diff
 * messages truncate values longer than {@value #MAX_VALUE_LENGTH}
 * characters.
 *
 * <p>Use as a {@link ValueMatcher ValueMatcher&lt;String&gt;} in any
 * surface that consumes one — e.g. a custom matcher attached to a
 * {@link org.javai.punit.api.Expectation}-bearing spec.
 *
 * @see ValueMatcher
 * @see JsonMatcher
 */
public final class StringMatcher implements ValueMatcher<String> {

    private static final int MAX_VALUE_LENGTH = 100;

    private final Mode mode;

    private StringMatcher(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /** Exact character-by-character equality. */
    public static StringMatcher exact() {
        return new StringMatcher(Mode.EXACT);
    }

    /** Case-insensitive comparison. */
    public static StringMatcher ignoreCase() {
        return new StringMatcher(Mode.IGNORE_CASE);
    }

    /** Strips leading and trailing whitespace before comparing. */
    public static StringMatcher trimWhitespace() {
        return new StringMatcher(Mode.TRIM_WHITESPACE);
    }

    /**
     * Strips leading and trailing whitespace and collapses internal
     * whitespace runs to single spaces before comparing.
     */
    public static StringMatcher normalizeWhitespace() {
        return new StringMatcher(Mode.NORMALIZE_WHITESPACE);
    }

    @Override
    public MatchResult match(String expected, String actual) {
        String description = description();

        if (expected == null && actual == null) {
            return MatchResult.pass(description, null, null);
        }
        if (expected == null) {
            return MatchResult.fail(description, null, actual,
                    "expected null but got: " + truncate(actual));
        }
        if (actual == null) {
            return MatchResult.fail(description, expected, null,
                    "expected: " + truncate(expected) + " but got null");
        }

        String transformedExpected = transform(expected);
        String transformedActual = transform(actual);

        boolean matches = switch (mode) {
            case EXACT, TRIM_WHITESPACE, NORMALIZE_WHITESPACE ->
                    transformedExpected.equals(transformedActual);
            case IGNORE_CASE ->
                    transformedExpected.equalsIgnoreCase(transformedActual);
        };

        if (matches) {
            return MatchResult.pass(description, expected, actual);
        }

        return MatchResult.fail(description, expected, actual,
                "expected: " + truncate(expected) + " but got: " + truncate(actual));
    }

    private String description() {
        return switch (mode) {
            case EXACT -> "string equals expected (exact)";
            case IGNORE_CASE -> "string equals expected (case-insensitive)";
            case TRIM_WHITESPACE -> "string equals expected (trim-whitespace)";
            case NORMALIZE_WHITESPACE -> "string equals expected (normalize-whitespace)";
        };
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
