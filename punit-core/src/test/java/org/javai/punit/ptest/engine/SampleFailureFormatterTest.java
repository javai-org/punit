package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SampleFailureFormatter}.
 */
class SampleFailureFormatterTest {

    private SampleFailureFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new SampleFailureFormatter();
    }

    @Nested
    @DisplayName("formatVerdictHint")
    class FormatVerdictHint {

        @Test
        @DisplayName("includes sample progress")
        void includesSampleProgress() {
            String hint = formatter.formatVerdictHint(5, 10, 100, 0.80);

            assertThat(hint).contains("10/100");
        }

        @Test
        @DisplayName("includes success count")
        void includesSuccessCount() {
            String hint = formatter.formatVerdictHint(7, 10, 100, 0.80);

            assertThat(hint).contains("7 successes so far");
        }

        @Test
        @DisplayName("includes threshold percentage")
        void includesThresholdPercentage() {
            String hint = formatter.formatVerdictHint(5, 10, 100, 0.95);

            assertThat(hint).contains("need 0.9500");
        }

        @Test
        @DisplayName("includes console redirect")
        void includesConsoleRedirect() {
            String hint = formatter.formatVerdictHint(5, 10, 100, 0.80);

            assertThat(hint).contains("see console for final verdict");
        }

        @Test
        @DisplayName("formats complete hint correctly")
        void formatsCompleteHintCorrectly() {
            String hint = formatter.formatVerdictHint(3, 5, 20, 0.75);

            assertThat(hint).isEqualTo(
                    "[PUnit sample 5/20: 3 successes so far, need 0.7500 - see console for final verdict]");
        }

        @Test
        @DisplayName("handles zero successes")
        void handlesZeroSuccesses() {
            String hint = formatter.formatVerdictHint(0, 1, 10, 0.80);

            assertThat(hint).contains("0 successes so far");
        }

        @Test
        @DisplayName("handles 100% threshold")
        void handles100PercentThreshold() {
            String hint = formatter.formatVerdictHint(5, 5, 10, 1.0);

            assertThat(hint).contains("need 1.0000");
        }

        @Test
        @DisplayName("handles fractional threshold rounding")
        void handlesFractionalThresholdRounding() {
            // 0.955 should round to 96%
            String hint = formatter.formatVerdictHint(5, 10, 100, 0.955);

            assertThat(hint).contains("need 0.9550");
        }
    }

    @Nested
    @DisplayName("extractFailureReason")
    class ExtractFailureReason {

        @Test
        @DisplayName("returns message for simple exception")
        void returnsMessageForSimpleException() {
            Throwable failure = new AssertionError("Expected true but was false");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("Expected true but was false");
        }

        @Test
        @DisplayName("returns class name for null message")
        void returnsClassNameForNullMessage() {
            // NullPointerException with no message returns null from getMessage()
            Throwable failure = new NullPointerException();

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("NullPointerException");
        }

        @Test
        @DisplayName("returns class name for blank message")
        void returnsClassNameForBlankMessage() {
            Throwable failure = new AssertionError("   ");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("AssertionError");
        }

        @Test
        @DisplayName("returns class name for empty message")
        void returnsClassNameForEmptyMessage() {
            Throwable failure = new AssertionError("");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("AssertionError");
        }

        @Test
        @DisplayName("takes first line of multi-line message")
        void takesFirstLineOfMultiLineMessage() {
            Throwable failure = new AssertionError("First line\nSecond line\nThird line");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("First line");
        }

        @Test
        @DisplayName("skips leading blank lines (AssertJ pattern)")
        void skipsLeadingBlankLines() {
            // AssertJ often starts messages with a newline
            Throwable failure = new AssertionError("\n\nActual content here\nMore details");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("Actual content here");
        }

        @Test
        @DisplayName("trims whitespace from result")
        void trimsWhitespaceFromResult() {
            Throwable failure = new AssertionError("  Message with spaces  ");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("Message with spaces");
        }

        @Test
        @DisplayName("handles RuntimeException")
        void handlesRuntimeException() {
            Throwable failure = new RuntimeException("Runtime error occurred");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("Runtime error occurred");
        }

        @Test
        @DisplayName("handles null failure gracefully")
        void handlesNullFailureGracefully() {
            String reason = formatter.extractFailureReason(null);

            assertThat(reason).isEqualTo("Unknown failure");
        }

        @Test
        @DisplayName("handles exception with only whitespace lines")
        void handlesExceptionWithOnlyWhitespaceLines() {
            Throwable failure = new AssertionError("\n   \n  \n");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("AssertionError");
        }

        @Test
        @DisplayName("preserves special characters in message")
        void preservesSpecialCharactersInMessage() {
            Throwable failure = new AssertionError("Expected: <100> but was: <200>");

            String reason = formatter.extractFailureReason(failure);

            assertThat(reason).isEqualTo("Expected: <100> but was: <200>");
        }
    }

    @Nested
    @DisplayName("formatSampleFailure")
    class FormatSampleFailure {

        @Test
        @DisplayName("combines hint and reason")
        void combinesHintAndReason() {
            Throwable failure = new AssertionError("Test failed");

            String formatted = formatter.formatSampleFailure(failure, 3, 5, 10, 0.80);

            assertThat(formatted).contains("[PUnit sample 5/10:");
            assertThat(formatted).contains("Test failed");
        }

        @Test
        @DisplayName("separates hint and reason with newline")
        void separatesHintAndReasonWithNewline() {
            Throwable failure = new AssertionError("Test failed");

            String formatted = formatter.formatSampleFailure(failure, 3, 5, 10, 0.80);

            assertThat(formatted).contains("]\nTest failed");
        }

        @Test
        @DisplayName("handles null failure")
        void handlesNullFailure() {
            String formatted = formatter.formatSampleFailure(null, 3, 5, 10, 0.80);

            assertThat(formatted).contains("[PUnit sample");
            assertThat(formatted).contains("Unknown failure");
        }

        @Test
        @DisplayName("formats complete message correctly")
        void formatsCompleteMessageCorrectly() {
            Throwable failure = new AssertionError("Expected valid JSON");

            String formatted = formatter.formatSampleFailure(failure, 7, 8, 20, 0.90);

            assertThat(formatted).isEqualTo(
                    "[PUnit sample 8/20: 7 successes so far, need 0.9000 - see console for final verdict]\n" +
                    "Expected valid JSON");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles first sample failure")
        void handlesFirstSampleFailure() {
            String hint = formatter.formatVerdictHint(0, 1, 100, 0.80);

            assertThat(hint).contains("1/100");
            assertThat(hint).contains("0 successes");
        }

        @Test
        @DisplayName("handles last sample")
        void handlesLastSample() {
            String hint = formatter.formatVerdictHint(79, 100, 100, 0.80);

            assertThat(hint).contains("100/100");
            assertThat(hint).contains("79 successes");
        }

        @Test
        @DisplayName("handles very long exception message")
        void handlesVeryLongExceptionMessage() {
            String longMessage = "A".repeat(1000) + "\nSecond line";
            Throwable failure = new AssertionError(longMessage);

            String reason = formatter.extractFailureReason(failure);

            // Should take first line only
            assertThat(reason).isEqualTo("A".repeat(1000));
        }

        @Test
        @DisplayName("handles exception with cause")
        void handlesExceptionWithCause() {
            Throwable cause = new RuntimeException("Root cause");
            Throwable failure = new AssertionError("Wrapper message", cause);

            String reason = formatter.extractFailureReason(failure);

            // Should use the wrapper's message, not the cause
            assertThat(reason).isEqualTo("Wrapper message");
        }
    }
}

