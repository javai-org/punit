package org.javai.punit.spec.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher.MatchResult;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BaselineSelector}.
 */
@DisplayName("BaselineSelector")
class BaselineSelectorTest {

    private final BaselineSelector selector = new BaselineSelector();

    private ExecutionSpecification minimalSpec() {
        return ExecutionSpecification.builder()
            .useCaseId("test")
            .empiricalBasis(100, 95)
            .build();
    }

    private BaselineCandidate candidate(String name, CovariateProfile profile, Instant generatedAt) {
        return new BaselineCandidate(
            name + ".yaml",
            "footprint",
            profile,
            generatedAt,
            minimalSpec()
        );
    }

    // Declaration for tests that use "region" covariate
    private CovariateDeclaration regionDeclaration() {
        return new CovariateDeclaration(
            List.of(StandardCovariate.REGION),
            Map.of()
        );
    }

    // Declaration for tests that use "region" and "timezone" covariates
    private CovariateDeclaration regionAndTimezoneDeclaration() {
        return new CovariateDeclaration(
            List.of(StandardCovariate.REGION, StandardCovariate.TIMEZONE),
            Map.of()
        );
    }

    @Nested
    @DisplayName("empty candidates")
    class EmptyCandidatesTests {

        @Test
        @DisplayName("should return noMatch for empty candidate list")
        void shouldReturnNoMatchForEmptyCandidateList() {
            var result = selector.select(List.of(), CovariateProfile.empty(), CovariateDeclaration.EMPTY);
            
            assertThat(result.hasSelection()).isFalse();
            assertThat(result.candidateCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("single candidate")
    class SingleCandidateTests {

        @Test
        @DisplayName("should select the only candidate")
        void shouldSelectTheOnlyCandidate() {
            var candidateProfile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var testProfile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var candidate = candidate("baseline1", candidateProfile, Instant.now());
            
            var result = selector.select(List.of(candidate), testProfile, regionDeclaration());
            
            assertThat(result.hasSelection()).isTrue();
            assertThat(result.selected()).isEqualTo(candidate);
            assertThat(result.ambiguous()).isFalse();
        }
    }

    @Nested
    @DisplayName("covariate matching")
    class CovariateMatchingTests {

        @Test
        @DisplayName("should prefer candidate with more matching covariates")
        void shouldPreferCandidateWithMoreMatchingCovariates() {
            var now = Instant.now();
            
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/Paris") // Doesn't match
                .build();
            var profile2 = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London") // Matches
                .build();
            
            var testProfile = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London")
                .build();
            
            var candidate1 = candidate("baseline1", profile1, now);
            var candidate2 = candidate("baseline2", profile2, now);
            
            var result = selector.select(List.of(candidate1, candidate2), testProfile, regionAndTimezoneDeclaration());
            
            assertThat(result.selected().filename()).isEqualTo("baseline2.yaml");
            assertThat(result.hasNonConformance()).isFalse();
        }

        @Test
        @DisplayName("should detect non-conformance")
        void shouldDetectNonConformance() {
            var baselineProfile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var testProfile = CovariateProfile.builder()
                .put("region", "US")
                .build();
            
            var candidate = candidate("baseline1", baselineProfile, Instant.now());
            
            var result = selector.select(List.of(candidate), testProfile, regionDeclaration());
            
            assertThat(result.hasSelection()).isTrue();
            assertThat(result.hasNonConformance()).isTrue();
            assertThat(result.nonConformingDetails()).hasSize(1);
            assertThat(result.nonConformingDetails().get(0).covariateKey()).isEqualTo("region");
            assertThat(result.nonConformingDetails().get(0).result()).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("tie-breaking")
    class TieBreakingTests {

        @Test
        @DisplayName("should prefer more recent baseline in ties")
        void shouldPreferMoreRecentBaselineInTies() {
            var older = Instant.parse("2026-01-01T00:00:00Z");
            var newer = Instant.parse("2026-01-10T00:00:00Z");
            
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var candidate1 = candidate("older", profile, older);
            var candidate2 = candidate("newer", profile, newer);
            
            var result = selector.select(List.of(candidate1, candidate2), profile, regionDeclaration());
            
            assertThat(result.selected().filename()).isEqualTo("newer.yaml");
        }
    }

    @Nested
    @DisplayName("ambiguity detection")
    class AmbiguityDetectionTests {

        @Test
        @DisplayName("should detect ambiguous selection")
        void shouldDetectAmbiguousSelection() {
            var now = Instant.now();
            
            // Same profile, same timestamp = ambiguous
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var candidate1 = candidate("baseline1", profile, now);
            var candidate2 = candidate("baseline2", profile, now);
            
            var result = selector.select(List.of(candidate1, candidate2), profile, regionDeclaration());
            
            assertThat(result.ambiguous()).isTrue();
        }

        @Test
        @DisplayName("should not flag as ambiguous when candidates differ")
        void shouldNotFlagAsAmbiguousWhenCandidatesDiffer() {
            var now = Instant.now();
            
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("region", "US")
                .build();
            
            var testProfile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var candidate1 = candidate("baseline1", profile1, now);
            var candidate2 = candidate("baseline2", profile2, now);
            
            var result = selector.select(List.of(candidate1, candidate2), testProfile, regionDeclaration());
            
            assertThat(result.ambiguous()).isFalse();
        }
    }

    @Nested
    @DisplayName("conformance details")
    class ConformanceDetailsTests {

        @Test
        @DisplayName("should include all covariate conformance details")
        void shouldIncludeAllCovariateConformanceDetails() {
            var baselineProfile = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London")
                .build();
            var testProfile = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "America/New_York")
                .build();
            
            var candidate = candidate("baseline1", baselineProfile, Instant.now());
            
            var result = selector.select(List.of(candidate), testProfile, regionAndTimezoneDeclaration());
            
            assertThat(result.conformanceDetails()).hasSize(2);
            assertThat(result.conformanceDetails().get(0).covariateKey()).isEqualTo("region");
            assertThat(result.conformanceDetails().get(0).result()).isEqualTo(MatchResult.CONFORMS);
            assertThat(result.conformanceDetails().get(1).covariateKey()).isEqualTo("timezone");
            assertThat(result.conformanceDetails().get(1).result()).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("empty declaration")
    class EmptyDeclarationTests {

        @Test
        @DisplayName("with empty declaration all candidates are equal (first wins)")
        void withEmptyDeclarationAllCandidatesAreEqual() {
            var now = Instant.now();
            
            // Profile values don't matter when declaration is empty
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("region", "US")
                .build();
            
            var candidate1 = candidate("first", profile1, now);
            var candidate2 = candidate("second", profile2, now);
            
            // With EMPTY declaration, no scoring happens - all candidates are equivalent
            var result = selector.select(List.of(candidate1, candidate2), CovariateProfile.empty(), CovariateDeclaration.EMPTY);
            
            // All candidates are ambiguous since none can be distinguished
            assertThat(result.hasSelection()).isTrue();
            assertThat(result.ambiguous()).isTrue();
            assertThat(result.conformanceDetails()).isEmpty();
        }
    }
}
