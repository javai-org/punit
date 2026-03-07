package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.RegionGroupDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateResolverRegistry}.
 */
@DisplayName("CovariateResolverRegistry")
class CovariateResolverRegistryTest {

    @Nested
    @DisplayName("builder()")
    class BuilderTests {

        @Test
        @DisplayName("should build registry with registered resolvers")
        void shouldBuildRegistryWithRegisteredResolvers() {
            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(List.of()))
                .build();

            assertThat(registry.hasResolver("region")).isTrue();
            assertThat(registry.getResolver("region")).isInstanceOf(RegionResolver.class);
        }

        @Test
        @DisplayName("should support multiple resolver registrations")
        void shouldSupportMultipleResolverRegistrations() {
            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(List.of()))
                .register("timezone", new TimezoneResolver())
                .build();

            assertThat(registry.hasResolver("region")).isTrue();
            assertThat(registry.hasResolver("timezone")).isTrue();
        }

        @Test
        @DisplayName("later registration should override earlier for same key")
        void laterRegistrationShouldOverrideEarlier() {
            var customResolver = (CovariateResolver) ctx ->
                new CovariateValue.StringValue("overridden");

            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(List.of()))
                .register("region", customResolver)
                .build();

            assertThat(registry.getResolver("region")).isSameAs(customResolver);
        }

        @Test
        @DisplayName("should build empty registry")
        void shouldBuildEmptyRegistry() {
            var registry = CovariateResolverRegistry.builder().build();

            assertThat(registry.hasResolver("anything")).isFalse();
        }
    }

    @Nested
    @DisplayName("getResolver()")
    class GetResolverTests {

        @Test
        @DisplayName("should return registered resolver for known key")
        void shouldReturnRegisteredResolverForKnownKey() {
            var regionGroups = List.of(
                new RegionGroupDefinition(java.util.Set.of("US"), "US")
            );
            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(regionGroups))
                .build();

            assertThat(registry.getResolver("region")).isInstanceOf(RegionResolver.class);
        }

        @Test
        @DisplayName("should return CustomCovariateResolver for unknown keys")
        void shouldReturnCustomCovariateResolverForUnknownKeys() {
            var registry = CovariateResolverRegistry.builder().build();

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
            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(List.of()))
                .register("timezone", new TimezoneResolver())
                .build();

            assertThat(registry.hasResolver("region")).isTrue();
            assertThat(registry.hasResolver("timezone")).isTrue();
        }

        @Test
        @DisplayName("should return false for unregistered keys")
        void shouldReturnFalseForUnregisteredKeys() {
            var registry = CovariateResolverRegistry.builder()
                .register("region", new RegionResolver(List.of()))
                .build();

            assertThat(registry.hasResolver("unknown_key")).isFalse();
        }
    }
}
