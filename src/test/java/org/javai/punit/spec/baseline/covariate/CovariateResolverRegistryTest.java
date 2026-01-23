package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.model.CovariateValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateResolverRegistry}.
 */
@DisplayName("CovariateResolverRegistry")
class CovariateResolverRegistryTest {

    @Nested
    @DisplayName("withStandardResolvers()")
    class WithStandardResolversTests {

        @Test
        @DisplayName("should have resolver for WEEKDAY_VERSUS_WEEKEND")
        void shouldHaveResolverForWeekdayVsWeekend() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key())).isTrue();
            assertThat(registry.getResolver(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key()))
                .isInstanceOf(WeekdayVsWeekendResolver.class);
        }

        @Test
        @DisplayName("should have resolver for TIME_OF_DAY")
        void shouldHaveResolverForTimeOfDay() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver(StandardCovariate.TIME_OF_DAY.key())).isTrue();
            assertThat(registry.getResolver(StandardCovariate.TIME_OF_DAY.key()))
                .isInstanceOf(TimeOfDayResolver.class);
        }

        @Test
        @DisplayName("should have resolver for TIMEZONE")
        void shouldHaveResolverForTimezone() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver(StandardCovariate.TIMEZONE.key())).isTrue();
            assertThat(registry.getResolver(StandardCovariate.TIMEZONE.key()))
                .isInstanceOf(TimezoneResolver.class);
        }

        @Test
        @DisplayName("should have resolver for REGION")
        void shouldHaveResolverForRegion() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver(StandardCovariate.REGION.key())).isTrue();
            assertThat(registry.getResolver(StandardCovariate.REGION.key()))
                .isInstanceOf(RegionResolver.class);
        }
    }

    @Nested
    @DisplayName("getResolver()")
    class GetResolverTests {

        @Test
        @DisplayName("should return CustomCovariateResolver for unknown keys")
        void shouldReturnCustomCovariateResolverForUnknownKeys() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            var resolver = registry.getResolver("unknown_custom_key");
            
            assertThat(resolver).isInstanceOf(CustomCovariateResolver.class);
            assertThat(((CustomCovariateResolver) resolver).getKey()).isEqualTo("unknown_custom_key");
        }

        @Test
        @DisplayName("should return registered resolver for custom keys")
        void shouldReturnRegisteredResolverForCustomKeys() {
            var customResolver = (CovariateResolver) ctx -> 
                new CovariateValue.StringValue("custom-value");
            
            var registry = CovariateResolverRegistry.builder()
                .register("my_custom_key", customResolver)
                .build();
            
            assertThat(registry.getResolver("my_custom_key")).isSameAs(customResolver);
        }
    }

    @Nested
    @DisplayName("hasResolver()")
    class HasResolverTests {

        @Test
        @DisplayName("should return true for registered keys")
        void shouldReturnTrueForRegisteredKeys() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver("weekday_vs_weekend")).isTrue();
            assertThat(registry.hasResolver("time_of_day")).isTrue();
        }

        @Test
        @DisplayName("should return false for unregistered keys")
        void shouldReturnFalseForUnregisteredKeys() {
            var registry = CovariateResolverRegistry.withStandardResolvers();
            
            assertThat(registry.hasResolver("unknown_key")).isFalse();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("custom registration should override standard")
        void customRegistrationShouldOverrideStandard() {
            var customResolver = (CovariateResolver) ctx -> 
                new CovariateValue.StringValue("overridden");
            
            var registry = CovariateResolverRegistry.builder()
                .register(StandardCovariate.REGION, new RegionResolver())
                .register(StandardCovariate.REGION, customResolver)
                .build();
            
            assertThat(registry.getResolver(StandardCovariate.REGION.key()))
                .isSameAs(customResolver);
        }
    }
}

