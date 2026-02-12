package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.model.CovariateValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateMatcherRegistry}.
 */
@DisplayName("CovariateMatcherRegistry")
class CovariateMatcherRegistryTest {

    @Nested
    @DisplayName("withDefaultMatchers()")
    class WithDefaultMatchersTests {

        @Test
        @DisplayName("should have case-insensitive ExactStringMatcher for region")
        void shouldHaveCaseInsensitiveMatcherForRegion() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            assertThat(registry.hasMatcher("region")).isTrue();
            assertThat(registry.getMatcher("region")).isInstanceOf(ExactStringMatcher.class);

            // Verify case-insensitivity: "us" should match "US"
            var matcher = registry.getMatcher("region");
            var result = matcher.match(
                new CovariateValue.StringValue("US"),
                new CovariateValue.StringValue("us")
            );
            assertThat(result).isEqualTo(CovariateMatcher.MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("should not have explicit matchers for day_of_week or time_of_day")
        void shouldNotHaveExplicitMatchersForDayOrTime() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            assertThat(registry.hasMatcher("day_of_week")).isFalse();
            assertThat(registry.hasMatcher("time_of_day")).isFalse();
        }

        @Test
        @DisplayName("should not have explicit matcher for timezone")
        void shouldNotHaveExplicitMatcherForTimezone() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            assertThat(registry.hasMatcher("timezone")).isFalse();
        }
    }

    @Nested
    @DisplayName("getMatcher()")
    class GetMatcherTests {

        @Test
        @DisplayName("should return ExactStringMatcher for unknown keys")
        void shouldReturnExactStringMatcherForUnknownKeys() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            var matcher = registry.getMatcher("custom_covariate");

            assertThat(matcher).isInstanceOf(ExactStringMatcher.class);
        }

        @Test
        @DisplayName("default matcher should be case-sensitive")
        void defaultMatcherShouldBeCaseSensitive() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            var matcher = registry.getMatcher("some_unknown_key");
            var result = matcher.match(
                new CovariateValue.StringValue("Value"),
                new CovariateValue.StringValue("value")
            );

            assertThat(result).isEqualTo(CovariateMatcher.MatchResult.DOES_NOT_CONFORM);
        }

        @Test
        @DisplayName("default matcher should match identical strings")
        void defaultMatcherShouldMatchIdenticalStrings() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            var matcher = registry.getMatcher("day_of_week");
            var result = matcher.match(
                new CovariateValue.StringValue("WEEKEND"),
                new CovariateValue.StringValue("WEEKEND")
            );

            assertThat(result).isEqualTo(CovariateMatcher.MatchResult.CONFORMS);
        }
    }

    @Nested
    @DisplayName("hasMatcher()")
    class HasMatcherTests {

        @Test
        @DisplayName("should return true for registered keys")
        void shouldReturnTrueForRegisteredKeys() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            assertThat(registry.hasMatcher("region")).isTrue();
        }

        @Test
        @DisplayName("should return false for unregistered keys")
        void shouldReturnFalseForUnregisteredKeys() {
            var registry = CovariateMatcherRegistry.withDefaultMatchers();

            assertThat(registry.hasMatcher("unknown_key")).isFalse();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should support custom matcher registration")
        void shouldSupportCustomMatcherRegistration() {
            var registry = CovariateMatcherRegistry.builder()
                .register("custom_key", new ExactStringMatcher(false))
                .build();

            assertThat(registry.hasMatcher("custom_key")).isTrue();
            assertThat(registry.getMatcher("custom_key")).isInstanceOf(ExactStringMatcher.class);
        }
    }
}
