package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.model.CovariateDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateProfileResolver}.
 */
@DisplayName("CovariateProfileResolver")
class CovariateProfileResolverTest {

    private final CovariateProfileResolver resolver = new CovariateProfileResolver();

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("should return empty profile for empty declaration")
        void shouldReturnEmptyProfileForEmptyDeclaration() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            var profile = resolver.resolve(CovariateDeclaration.EMPTY, context);
            
            assertThat(profile.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should resolve all declared covariates")
        void shouldResolveAllDeclaredCovariates() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIMEZONE),
                Map.of()
            );
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-13T10:00:00Z")) // Tuesday
                .systemTimezone(ZoneId.of("Europe/London"))
                .build();
            
            var profile = resolver.resolve(declaration, context);
            
            assertThat(profile.size()).isEqualTo(2);
            assertThat(profile.get("weekday_vs_weekend").toCanonicalString()).isEqualTo("Mo-Fr");
            assertThat(profile.get("timezone").toCanonicalString()).isEqualTo("Europe/London");
        }

        @Test
        @DisplayName("should preserve declaration order")
        void shouldPreserveDeclarationOrder() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE, StandardCovariate.REGION),
                Map.of("custom1", CovariateCategory.OPERATIONAL)
            );
            var context = DefaultCovariateResolutionContext.forNow();
            
            var profile = resolver.resolve(declaration, context);
            
            assertThat(profile.orderedKeys())
                .containsExactly("timezone", "region", "custom1");
        }
    }
}

