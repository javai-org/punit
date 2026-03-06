package org.javai.punit.contract.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.contract.match.VerificationMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerificationMatcher")
class VerificationMatcherTest {

    @Nested
    @DisplayName("MatchResult")
    class MatchResultTests {

        @Test
        @DisplayName("match() creates successful result")
        void matchCreatesSuccessfulResult() {
            MatchResult result = MatchResult.match();

            assertThat(result.matches()).isTrue();
            assertThat(result.mismatches()).isFalse();
            assertThat(result.diff()).isEmpty();
        }

        @Test
        @DisplayName("mismatch() creates failure result with diff")
        void mismatchCreatesFailureResultWithDiff() {
            MatchResult result = MatchResult.mismatch("expected 'foo' but got 'bar'");

            assertThat(result.matches()).isFalse();
            assertThat(result.mismatches()).isTrue();
            assertThat(result.diff()).isEqualTo("expected 'foo' but got 'bar'");
        }

        @Test
        @DisplayName("mismatches() is inverse of matches()")
        void mismatchesIsInverseOfMatches() {
            assertThat(MatchResult.match().mismatches()).isFalse();
            assertThat(MatchResult.mismatch("diff").mismatches()).isTrue();
        }
    }

    @Nested
    @DisplayName("custom matcher")
    class CustomMatcherTests {

        @Test
        @DisplayName("can be implemented as lambda")
        void canBeImplementedAsLambda() {
            VerificationMatcher<Integer> evenMatcher = (expected, actual) -> {
                if (expected % 2 == actual % 2) {
                    return MatchResult.match();
                }
                return MatchResult.mismatch("parity mismatch");
            };

            assertThat(evenMatcher.match(2, 4).matches()).isTrue();
            assertThat(evenMatcher.match(2, 3).matches()).isFalse();
        }
    }
}
