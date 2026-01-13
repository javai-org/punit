package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
        @DisplayName("of() should create from arrays")
        void ofShouldCreateFromArrays() {
            var declaration = CovariateDeclaration.of(
                new StandardCovariate[] { StandardCovariate.WEEKDAY_VERSUS_WEEKEND },
                new String[] { "custom1" }
            );
            
            assertThat(declaration.standardCovariates())
                .containsExactly(StandardCovariate.WEEKDAY_VERSUS_WEEKEND);
            assertThat(declaration.customCovariates())
                .containsExactly("custom1");
        }

        @Test
        @DisplayName("of() with empty arrays should return EMPTY")
        void ofWithEmptyArraysShouldReturnEmpty() {
            var declaration = CovariateDeclaration.of(
                new StandardCovariate[] {},
                new String[] {}
            );
            
            assertThat(declaration).isSameAs(CovariateDeclaration.EMPTY);
        }

        @Test
        @DisplayName("should create immutable copies of lists")
        void shouldCreateImmutableCopiesOfLists() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                List.of("custom")
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
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.TIMEZONE),
                List.of("custom1", "custom2")
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
                List.of()
            );
            
            assertThat(declaration.allKeys()).containsExactly("region", "time_of_day");
        }

        @Test
        @DisplayName("should preserve custom covariate ordering")
        void shouldPreserveCustomCovariateOrdering() {
            var declaration = new CovariateDeclaration(
                List.of(),
                List.of("z_custom", "a_custom", "m_custom")
            );
            
            assertThat(declaration.allKeys()).containsExactly("z_custom", "a_custom", "m_custom");
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
                List.of("custom")
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
                List.of()
            );
            
            assertThat(declaration.computeDeclarationHash()).hasSize(8);
        }

        @Test
        @DisplayName("different declarations should produce different hashes")
        void differentDeclarationsShouldProduceDifferentHashes() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                List.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE),
                List.of()
            );
            
            assertThat(declaration1.computeDeclarationHash())
                .isNotEqualTo(declaration2.computeDeclarationHash());
        }

        @Test
        @DisplayName("ordering should affect hash")
        void orderingShouldAffectHash() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
                List.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE, StandardCovariate.REGION),
                List.of()
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
                List.of()
            );
            
            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when custom covariates present")
        void shouldReturnFalseWhenCustomCovariatesPresent() {
            var declaration = new CovariateDeclaration(
                List.of(),
                List.of("custom")
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
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
                List.of("custom1", "custom2", "custom3")
            );
            
            assertThat(declaration.size()).isEqualTo(5);
        }
    }
}

