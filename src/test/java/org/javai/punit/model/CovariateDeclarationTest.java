package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateDeclaration}.
 */
@DisplayName("CovariateDeclaration")
class CovariateDeclarationTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("EMPTY should have no covariates")
        void emptyShouldHaveNoCovariates() {
            assertThat(CovariateDeclaration.EMPTY.isEmpty()).isTrue();
            assertThat(CovariateDeclaration.EMPTY.size()).isEqualTo(0);
            assertThat(CovariateDeclaration.EMPTY.standardCovariates()).isEmpty();
            assertThat(CovariateDeclaration.EMPTY.customCovariates()).isEmpty();
        }

        @Test
        @DisplayName("of() should create from standard array and custom map")
        void ofShouldCreateFromArrayAndMap() {
            var declaration = CovariateDeclaration.of(
                new StandardCovariate[] { StandardCovariate.WEEKDAY_VERSUS_WEEKEND },
                Map.of("custom1", CovariateCategory.CONFIGURATION)
            );
            
            assertThat(declaration.standardCovariates())
                .containsExactly(StandardCovariate.WEEKDAY_VERSUS_WEEKEND);
            assertThat(declaration.customCovariates())
                .containsEntry("custom1", CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("of() with empty inputs should return EMPTY")
        void ofWithEmptyInputsShouldReturnEmpty() {
            var declaration = CovariateDeclaration.of(
                new StandardCovariate[] {},
                Map.of()
            );
            
            assertThat(declaration).isSameAs(CovariateDeclaration.EMPTY);
        }

        @Test
        @DisplayName("should create immutable copies")
        void shouldCreateImmutableCopies() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of("custom", CovariateCategory.OPERATIONAL)
            );
            
            assertThat(declaration.standardCovariates()).isUnmodifiable();
            assertThat(declaration.customCovariates()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("allKeys()")
    class AllKeysTests {

        @Test
        @DisplayName("should return empty list for empty declaration")
        void shouldReturnEmptyListForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.allKeys()).isEmpty();
        }

        @Test
        @DisplayName("should return standard keys first, then custom")
        void shouldReturnStandardKeysFirstThenCustom() {
            // Use LinkedHashMap to preserve order
            var customMap = new LinkedHashMap<String, CovariateCategory>();
            customMap.put("custom1", CovariateCategory.OPERATIONAL);
            customMap.put("custom2", CovariateCategory.CONFIGURATION);
            
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIMEZONE),
                customMap
            );
            
            assertThat(declaration.allKeys()).containsExactly(
                "weekday_vs_weekend",
                "timezone",
                "custom1",
                "custom2"
            );
        }

        @Test
        @DisplayName("should preserve standard covariate ordering")
        void shouldPreserveStandardCovariateOrdering() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIME_OF_DAY),
                Map.of()
            );
            
            assertThat(declaration.allKeys()).containsExactly("region", "time_of_day");
        }
    }

    @Nested
    @DisplayName("getCategory()")
    class GetCategoryTests {

        @Test
        @DisplayName("should return category for standard covariate")
        void shouldReturnCategoryForStandardCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration.getCategory("region"))
                .isEqualTo(CovariateCategory.OPERATIONAL);
        }

        @Test
        @DisplayName("should return category for custom covariate")
        void shouldReturnCategoryForCustomCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(),
                Map.of("llm_model", CovariateCategory.CONFIGURATION)
            );
            
            assertThat(declaration.getCategory("llm_model"))
                .isEqualTo(CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("should throw for unknown covariate")
        void shouldThrowForUnknownCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThatThrownBy(() -> declaration.getCategory("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared");
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("should return true for declared standard covariate")
        void shouldReturnTrueForDeclaredStandardCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration.contains("region")).isTrue();
        }

        @Test
        @DisplayName("should return true for declared custom covariate")
        void shouldReturnTrueForDeclaredCustomCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(),
                Map.of("custom", CovariateCategory.OPERATIONAL)
            );
            
            assertThat(declaration.contains("custom")).isTrue();
        }

        @Test
        @DisplayName("should return false for undeclared covariate")
        void shouldReturnFalseForUndeclaredCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration.contains("unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("computeDeclarationHash()")
    class ComputeDeclarationHashTests {

        @Test
        @DisplayName("should return empty string for empty declaration")
        void shouldReturnEmptyStringForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.computeDeclarationHash()).isEmpty();
        }

        @Test
        @DisplayName("hash should be stable across calls")
        void hashShouldBeStableAcrossCalls() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.WEEKDAY_VERSUS_WEEKEND),
                Map.of("custom", CovariateCategory.OPERATIONAL)
            );
            
            var hash1 = declaration.computeDeclarationHash();
            var hash2 = declaration.computeDeclarationHash();
            
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("hash should be 8 characters")
        void hashShouldBe8Characters() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration.computeDeclarationHash()).hasSize(8);
        }

        @Test
        @DisplayName("different declarations should produce different hashes")
        void differentDeclarationsShouldProduceDifferentHashes() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE),
                Map.of()
            );
            
            assertThat(declaration1.computeDeclarationHash())
                .isNotEqualTo(declaration2.computeDeclarationHash());
        }

        @Test
        @DisplayName("ordering should affect hash")
        void orderingShouldAffectHash() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
                Map.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE, StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration1.computeDeclarationHash())
                .isNotEqualTo(declaration2.computeDeclarationHash());
        }
    }

    @Nested
    @DisplayName("isEmpty()")
    class IsEmptyTests {

        @Test
        @DisplayName("should return true for empty declaration")
        void shouldReturnTrueForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should return false when standard covariates present")
        void shouldReturnFalseWhenStandardCovariatesPresent() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when custom covariates present")
        void shouldReturnFalseWhenCustomCovariatesPresent() {
            var declaration = new CovariateDeclaration(
                List.of(),
                Map.of("custom", CovariateCategory.OPERATIONAL)
            );
            
            assertThat(declaration.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("size()")
    class SizeTests {

        @Test
        @DisplayName("should return 0 for empty declaration")
        void shouldReturnZeroForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should count all covariates")
        void shouldCountAllCovariates() {
            var customMap = new LinkedHashMap<String, CovariateCategory>();
            customMap.put("custom1", CovariateCategory.OPERATIONAL);
            customMap.put("custom2", CovariateCategory.CONFIGURATION);
            customMap.put("custom3", CovariateCategory.DATA_STATE);
            
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
                customMap
            );
            
            assertThat(declaration.size()).isEqualTo(5);
        }
    }
}
