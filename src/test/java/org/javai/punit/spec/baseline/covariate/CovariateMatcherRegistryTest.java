package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.StandardCovariate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateMatcherRegistry}.
 */
@DisplayName("CovariateMatcherRegistry")
class CovariateMatcherRegistryTest {

    @Nested
    @DisplayName("withStandardMatchers()")
    class WithStandardMatchersTests {

        @Test
        @DisplayName("should have matcher for WEEKDAY_VERSUS_WEEKEND")
        void shouldHaveMatcherForWeekdayVsWeekend() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key())).isTrue();
            assertThat(registry.getMatcher(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key()))
                .isInstanceOf(WeekdayVsWeekendMatcher.class);
        }

        @Test
        @DisplayName("should have matcher for TIME_OF_DAY")
        void shouldHaveMatcherForTimeOfDay() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher(StandardCovariate.TIME_OF_DAY.key())).isTrue();
            assertThat(registry.getMatcher(StandardCovariate.TIME_OF_DAY.key()))
                .isInstanceOf(TimeOfDayMatcher.class);
        }

        @Test
        @DisplayName("should have matcher for TIMEZONE")
        void shouldHaveMatcherForTimezone() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher(StandardCovariate.TIMEZONE.key())).isTrue();
            assertThat(registry.getMatcher(StandardCovariate.TIMEZONE.key()))
                .isInstanceOf(ExactStringMatcher.class);
        }

        @Test
        @DisplayName("should have matcher for REGION")
        void shouldHaveMatcherForRegion() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher(StandardCovariate.REGION.key())).isTrue();
            assertThat(registry.getMatcher(StandardCovariate.REGION.key()))
                .isInstanceOf(ExactStringMatcher.class);
        }
    }

    @Nested
    @DisplayName("getMatcher()")
    class GetMatcherTests {

        @Test
        @DisplayName("should return ExactStringMatcher for custom covariates")
        void shouldReturnExactStringMatcherForCustomCovariates() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            var matcher = registry.getMatcher("custom_covariate");
            
            assertThat(matcher).isInstanceOf(ExactStringMatcher.class);
        }
    }

    @Nested
    @DisplayName("hasMatcher()")
    class HasMatcherTests {

        @Test
        @DisplayName("should return true for registered keys")
        void shouldReturnTrueForRegisteredKeys() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher("weekday_vs_weekend")).isTrue();
            assertThat(registry.hasMatcher("time_of_day")).isTrue();
        }

        @Test
        @DisplayName("should return false for unregistered keys")
        void shouldReturnFalseForUnregisteredKeys() {
            var registry = CovariateMatcherRegistry.withStandardMatchers();
            
            assertThat(registry.hasMatcher("unknown_key")).isFalse();
        }
    }
}

