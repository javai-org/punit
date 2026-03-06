package org.javai.punit.contract.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.contract.match.VerificationMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StringMatcher")
class StringMatcherTest {

    @Nested
    @DisplayName("exact()")
    class ExactTests {

        private final StringMatcher matcher = StringMatcher.exact();

        @Test
        @DisplayName("matches identical strings")
        void matchesIdenticalStrings() {
            MatchResult result = matcher.match("hello", "hello");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("does not match different strings")
        void doesNotMatchDifferentStrings() {
            MatchResult result = matcher.match("hello", "world");

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("hello").contains("world");
        }

        @Test
        @DisplayName("is case sensitive")
        void isCaseSensitive() {
            MatchResult result = matcher.match("Hello", "hello");

            assertThat(result.matches()).isFalse();
        }

        @Test
        @DisplayName("is whitespace sensitive")
        void isWhitespaceSensitive() {
            MatchResult result = matcher.match("hello", " hello ");

            assertThat(result.matches()).isFalse();
        }
    }

    @Nested
    @DisplayName("ignoreCase()")
    class IgnoreCaseTests {

        private final StringMatcher matcher = StringMatcher.ignoreCase();

        @Test
        @DisplayName("matches identical strings")
        void matchesIdenticalStrings() {
            MatchResult result = matcher.match("hello", "hello");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("matches strings with different case")
        void matchesStringsWithDifferentCase() {
            MatchResult result = matcher.match("Hello", "HELLO");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("does not match different strings")
        void doesNotMatchDifferentStrings() {
            MatchResult result = matcher.match("hello", "world");

            assertThat(result.matches()).isFalse();
        }

        @Test
        @DisplayName("is whitespace sensitive")
        void isWhitespaceSensitive() {
            MatchResult result = matcher.match("hello", " hello ");

            assertThat(result.matches()).isFalse();
        }
    }

    @Nested
    @DisplayName("trimWhitespace()")
    class TrimWhitespaceTests {

        private final StringMatcher matcher = StringMatcher.trimWhitespace();

        @Test
        @DisplayName("matches identical strings")
        void matchesIdenticalStrings() {
            MatchResult result = matcher.match("hello", "hello");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("matches strings with leading/trailing whitespace")
        void matchesStringsWithLeadingTrailingWhitespace() {
            MatchResult result = matcher.match("  hello  ", "hello");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("matches when both have different whitespace")
        void matchesWhenBothHaveDifferentWhitespace() {
            MatchResult result = matcher.match("  hello", "hello  ");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("is case sensitive")
        void isCaseSensitive() {
            MatchResult result = matcher.match("  Hello  ", "hello");

            assertThat(result.matches()).isFalse();
        }

        @Test
        @DisplayName("preserves internal whitespace")
        void preservesInternalWhitespace() {
            MatchResult result = matcher.match("hello world", "hello  world");

            assertThat(result.matches()).isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeWhitespace()")
    class NormalizeWhitespaceTests {

        private final StringMatcher matcher = StringMatcher.normalizeWhitespace();

        @Test
        @DisplayName("matches identical strings")
        void matchesIdenticalStrings() {
            MatchResult result = matcher.match("hello world", "hello world");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("normalizes multiple spaces")
        void normalizesMultipleSpaces() {
            MatchResult result = matcher.match("hello  world", "hello world");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("normalizes tabs and newlines")
        void normalizesTabsAndNewlines() {
            MatchResult result = matcher.match("hello\tworld", "hello\nworld");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("trims leading and trailing whitespace")
        void trimsLeadingAndTrailingWhitespace() {
            MatchResult result = matcher.match("  hello world  ", "hello world");

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("is case sensitive")
        void isCaseSensitive() {
            MatchResult result = matcher.match("Hello World", "hello world");

            assertThat(result.matches()).isFalse();
        }
    }

    @Nested
    @DisplayName("null handling")
    class NullHandlingTests {

        private final StringMatcher matcher = StringMatcher.exact();

        @Test
        @DisplayName("matches when both are null")
        void matchesWhenBothAreNull() {
            MatchResult result = matcher.match(null, null);

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("mismatches when expected is null")
        void mismatchesWhenExpectedIsNull() {
            MatchResult result = matcher.match(null, "actual");

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("null").contains("actual");
        }

        @Test
        @DisplayName("mismatches when actual is null")
        void mismatchesWhenActualIsNull() {
            MatchResult result = matcher.match("expected", null);

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("expected").contains("null");
        }
    }

    @Nested
    @DisplayName("diff message truncation")
    class DiffMessageTruncationTests {

        private final StringMatcher matcher = StringMatcher.exact();

        @Test
        @DisplayName("truncates long values in diff message")
        void truncatesLongValuesInDiffMessage() {
            String longExpected = "a".repeat(150);
            String longActual = "b".repeat(150);

            MatchResult result = matcher.match(longExpected, longActual);

            assertThat(result.diff())
                    .contains("...")
                    .contains("150 chars");
        }

        @Test
        @DisplayName("does not truncate short values")
        void doesNotTruncateShortValues() {
            MatchResult result = matcher.match("short", "value");

            assertThat(result.diff())
                    .doesNotContain("...")
                    .doesNotContain("chars");
        }
    }
}
