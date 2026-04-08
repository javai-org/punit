package org.javai.punit.contract.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.contract.match.VerificationMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonMatcher")
class JsonMatcherTest {

    @Nested
    @DisplayName("availability")
    class AvailabilityTests {

        @Test
        @DisplayName("isAvailable() always returns true")
        void isAvailableAlwaysReturnsTrue() {
            assertThat(JsonMatcher.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("creation")
    class CreationTests {

        @Test
        @DisplayName("create() returns matcher")
        void createReturnsMatcher() {
            JsonMatcher matcher = JsonMatcher.create();

            assertThat(matcher).isNotNull();
        }
    }

    @Nested
    @DisplayName("matching")
    class MatchingTests {

        @Test
        @DisplayName("matches semantically equal JSON")
        void matchesSemanticallyEqualJson() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\",\"age\":30}",
                    "{\"age\":30,\"name\":\"Alice\"}"
            );

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("matches JSON with different whitespace")
        void matchesJsonWithDifferentWhitespace() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\"}",
                    "{ \"name\" : \"Alice\" }"
            );

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("detects added property")
        void detectsAddedProperty() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\"}",
                    "{\"name\":\"Alice\",\"age\":30}"
            );

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("add").contains("age");
        }

        @Test
        @DisplayName("detects removed property")
        void detectsRemovedProperty() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\",\"age\":30}",
                    "{\"name\":\"Alice\"}"
            );

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("remove").contains("age");
        }

        @Test
        @DisplayName("detects replaced value")
        void detectsReplacedValue() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\"}",
                    "{\"name\":\"Bob\"}"
            );

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("replace").contains("name");
        }

        @Test
        @DisplayName("handles array comparison")
        void handlesArrayComparison() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "[1,2,3]",
                    "[1,2,3]"
            );

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("detects array differences")
        void detectsArrayDifferences() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "[1,2,3]",
                    "[1,2,4]"
            );

            assertThat(result.matches()).isFalse();
        }

        @Test
        @DisplayName("handles invalid expected JSON")
        void handlesInvalidExpectedJson() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "not valid json",
                    "{\"name\":\"Alice\"}"
            );

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("expected value is not valid JSON");
        }

        @Test
        @DisplayName("handles invalid actual JSON")
        void handlesInvalidActualJson() {
            JsonMatcher matcher = JsonMatcher.create();

            MatchResult result = matcher.match(
                    "{\"name\":\"Alice\"}",
                    "not valid json"
            );

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("actual value is not valid JSON");
        }
    }

    @Nested
    @DisplayName("null handling")
    class NullHandlingTests {

        private final JsonMatcher matcher = JsonMatcher.create();

        @Test
        @DisplayName("matches when both are null")
        void matchesWhenBothAreNull() {
            MatchResult result = matcher.match(null, null);

            assertThat(result.matches()).isTrue();
        }

        @Test
        @DisplayName("mismatches when expected is null")
        void mismatchesWhenExpectedIsNull() {
            MatchResult result = matcher.match(null, "{\"key\":\"value\"}");

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("expected null");
        }

        @Test
        @DisplayName("mismatches when actual is null")
        void mismatchesWhenActualIsNull() {
            MatchResult result = matcher.match("{\"key\":\"value\"}", null);

            assertThat(result.matches()).isFalse();
            assertThat(result.diff()).contains("got null");
        }
    }
}
