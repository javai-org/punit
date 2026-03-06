package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExactStringMatcher}.
 */
@DisplayName("ExactStringMatcher")
class ExactStringMatcherTest {

    @Nested
    @DisplayName("case-sensitive matching")
    class CaseSensitiveTests {

        private final ExactStringMatcher matcher = new ExactStringMatcher(true);

        @Test
        @DisplayName("exact match should conform")
        void exactMatchShouldConform() {
            var baseline = new CovariateValue.StringValue("Europe/London");
            var test = new CovariateValue.StringValue("Europe/London");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("different case should not conform")
        void differentCaseShouldNotConform() {
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue("eu");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }

        @Test
        @DisplayName("different values should not conform")
        void differentValuesShouldNotConform() {
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue("US");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("case-insensitive matching")
    class CaseInsensitiveTests {

        private final ExactStringMatcher matcher = new ExactStringMatcher(false);

        @Test
        @DisplayName("same case should conform")
        void sameCaseShouldConform() {
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue("EU");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("different case should conform")
        void differentCaseShouldConform() {
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue("eu");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("mixed case should conform")
        void mixedCaseShouldConform() {
            var baseline = new CovariateValue.StringValue("Europe");
            var test = new CovariateValue.StringValue("EUROPE");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }
    }

    @Nested
    @DisplayName("NOT_SET handling")
    class NotSetTests {

        private final ExactStringMatcher matcher = new ExactStringMatcher();

        @Test
        @DisplayName("NOT_SET should never match itself")
        void notSetShouldNeverMatchItself() {
            var baseline = new CovariateValue.StringValue(CovariateProfile.UNDEFINED);
            var test = new CovariateValue.StringValue(CovariateProfile.UNDEFINED);
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }

        @Test
        @DisplayName("NOT_SET in baseline should not match any value")
        void notSetInBaselineShouldNotMatchAnyValue() {
            var baseline = new CovariateValue.StringValue(CovariateProfile.UNDEFINED);
            var test = new CovariateValue.StringValue("EU");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }

        @Test
        @DisplayName("NOT_SET in test should not match any baseline")
        void notSetInTestShouldNotMatchAnyBaseline() {
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue(CovariateProfile.UNDEFINED);
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("default constructor")
    class DefaultConstructorTests {

        @Test
        @DisplayName("default constructor should be case-sensitive")
        void defaultConstructorShouldBeCaseSensitive() {
            var matcher = new ExactStringMatcher();
            
            var baseline = new CovariateValue.StringValue("EU");
            var test = new CovariateValue.StringValue("eu");
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }
}

