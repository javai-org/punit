package org.javai.punit.spec.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.model.CovariateDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FootprintComputer}.
 */
@DisplayName("FootprintComputer")
class FootprintComputerTest {

    private final FootprintComputer computer = new FootprintComputer();

    @Nested
    @DisplayName("stability")
    class StabilityTests {

        @Test
        @DisplayName("same inputs should produce same hash")
        void sameInputsShouldProduceSameHash() {
            var declaration = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            
            var hash1 = computer.computeFootprint("shopping.search", Map.of("country", "UK"), declaration);
            var hash2 = computer.computeFootprint("shopping.search", Map.of("country", "UK"), declaration);
            
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("hash should be 8 characters")
        void hashShouldBe8Characters() {
            var hash = computer.computeFootprint("my.use.case");
            
            assertThat(hash).hasSize(8);
        }
    }

    @Nested
    @DisplayName("use case ID")
    class UseCaseIdTests {

        @Test
        @DisplayName("different use case IDs should produce different hashes")
        void differentUseCaseIdsShouldProduceDifferentHashes() {
            var hash1 = computer.computeFootprint("shopping.search");
            var hash2 = computer.computeFootprint("shopping.checkout");
            
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("factors")
    class FactorsTests {

        @Test
        @DisplayName("different factors should produce different hashes")
        void differentFactorsShouldProduceDifferentHashes() {
            var hash1 = computer.computeFootprint("usecase", Map.of("country", "UK"), CovariateDeclaration.EMPTY);
            var hash2 = computer.computeFootprint("usecase", Map.of("country", "US"), CovariateDeclaration.EMPTY);
            
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("factor order should not matter")
        void factorOrderShouldNotMatter() {
            // LinkedHashMap preserves insertion order, but hash should be independent
            var factors1 = new java.util.LinkedHashMap<String, Object>();
            factors1.put("a", "1");
            factors1.put("b", "2");
            
            var factors2 = new java.util.LinkedHashMap<String, Object>();
            factors2.put("b", "2");
            factors2.put("a", "1");
            
            var hash1 = computer.computeFootprint("usecase", factors1, CovariateDeclaration.EMPTY);
            var hash2 = computer.computeFootprint("usecase", factors2, CovariateDeclaration.EMPTY);
            
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("empty factors should produce same hash as no factors")
        void emptyFactorsShouldProduceSameHashAsNoFactors() {
            var hash1 = computer.computeFootprint("usecase", Map.of(), CovariateDeclaration.EMPTY);
            var hash2 = computer.computeFootprint("usecase", CovariateDeclaration.EMPTY);
            
            assertThat(hash1).isEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("covariate declarations")
    class CovariateDeclarationTests {

        @Test
        @DisplayName("different covariate declarations should produce different hashes")
        void differentCovariateDeclarationsShouldProduceDifferentHashes() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION),
                Map.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE),
                Map.of()
            );
            
            var hash1 = computer.computeFootprint("usecase", declaration1);
            var hash2 = computer.computeFootprint("usecase", declaration2);
            
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("covariate ordering should matter")
        void covariateOrderingShouldMatter() {
            var declaration1 = new CovariateDeclaration(
                List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
                Map.of()
            );
            var declaration2 = new CovariateDeclaration(
                List.of(StandardCovariate.TIMEZONE, StandardCovariate.REGION),
                Map.of()
            );
            
            var hash1 = computer.computeFootprint("usecase", declaration1);
            var hash2 = computer.computeFootprint("usecase", declaration2);
            
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("empty declaration should produce same hash as no covariates")
        void emptyDeclarationShouldProduceSameHashAsNoCovariates() {
            var hash1 = computer.computeFootprint("usecase", CovariateDeclaration.EMPTY);
            var hash2 = computer.computeFootprint("usecase");
            
            assertThat(hash1).isEqualTo(hash2);
        }
    }
}

