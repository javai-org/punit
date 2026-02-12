package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.BaselineRepository;
import org.javai.punit.spec.baseline.NoCompatibleBaselineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for covariate integration in {@link ProbabilisticTestExtension}.
 *
 * <p>These tests verify that the extension correctly:
 * <ul>
 *   <li>Selects baselines based on covariates when declared</li>
 *   <li>Falls back to simple loading when no covariates declared</li>
 *   <li>Throws appropriate exceptions when no matching baseline found</li>
 * </ul>
 */
@DisplayName("ProbabilisticTestExtension covariate integration")
class ProbabilisticTestExtensionCovariateTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("BaselineRepository candidate lookup")
    class BaselineRepositoryCandidateLookup {

        @Test
        @DisplayName("should find candidates with matching footprint")
        void baselineRepository_findsCandidatesWithMatchingFootprint() throws IOException {
            writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "WEEKDAY");
            writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "WEEKEND");

            BaselineRepository repository = new BaselineRepository(tempDir);

            var candidates = repository.findCandidates("TestUseCase", "abc12345");

            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).footprint()).isEqualTo("abc12345");
            assertThat(candidates.get(0).covariateProfile().get("day_of_week").toCanonicalString())
                    .isEqualTo("WEEKDAY");
        }

        @Test
        @DisplayName("should return empty when no matching footprint")
        void baselineRepository_returnsEmptyWhenNoMatchingFootprint() throws IOException {
            writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "WEEKDAY");

            BaselineRepository repository = new BaselineRepository(tempDir);

            var candidates = repository.findCandidates("TestUseCase", "xyz99999");

            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("should return all candidates when finding all")
        void baselineRepository_findAllCandidatesReturnsAll() throws IOException {
            writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "WEEKDAY");
            writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "WEEKEND");

            BaselineRepository repository = new BaselineRepository(tempDir);

            var candidates = repository.findAllCandidates("TestUseCase");

            assertThat(candidates).hasSize(2);
        }

        @Test
        @DisplayName("should find available footprints")
        void baselineRepository_findAvailableFootprints() throws IOException {
            writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "WEEKDAY");
            writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "WEEKEND");

            BaselineRepository repository = new BaselineRepository(tempDir);

            var footprints = repository.findAvailableFootprints("TestUseCase");

            assertThat(footprints).hasSize(2);
            assertThat(footprints).contains("abc12345", "def98765");
        }
    }

    @Nested
    @DisplayName("NoCompatibleBaselineException")
    class NoCompatibleBaselineExceptionTests {

        @Test
        @DisplayName("should contain useful information")
        void noCompatibleBaselineException_containsUsefulInformation() {
            NoCompatibleBaselineException ex = new NoCompatibleBaselineException(
                    "MyUseCase",
                    "expected123",
                    java.util.List.of("actual456", "actual789"));

            assertThat(ex.getUseCaseId()).isEqualTo("MyUseCase");
            assertThat(ex.getExpectedFootprint()).isEqualTo("expected123");
            assertThat(ex.getAvailableFootprints()).containsExactly("actual456", "actual789");

            String message = ex.getMessage();
            assertThat(message)
                    .contains("MyUseCase")
                    .contains("expected123")
                    .contains("actual456")
                    .contains("actual789")
                    .contains("MEASURE experiment");
        }

        @Test
        @DisplayName("should handle empty available footprints")
        void noCompatibleBaselineException_handlesEmptyAvailableFootprints() {
            NoCompatibleBaselineException ex = new NoCompatibleBaselineException(
                    "MyUseCase",
                    "expected123",
                    java.util.List.of());

            assertThat(ex.getMessage()).contains("No baselines found");
        }
    }

    @Nested
    @DisplayName("Covariate profile loading from spec")
    class CovariateProfileLoadingTests {

        @Test
        @DisplayName("should load covariate profile with time_of_day and day_of_week")
        void baselineRepository_loadsCovariateProfileFromSpec() throws IOException {
            String content = createSpecContentWithTimeOfDay("TestUseCase", "abc12345",
                    "08:00/2h", "WEEKDAY");
            Files.writeString(tempDir.resolve("TestUseCase-abc12345.yaml"), content);

            BaselineRepository repository = new BaselineRepository(tempDir);
            var candidates = repository.findCandidates("TestUseCase", "abc12345");

            assertThat(candidates).hasSize(1);
            CovariateProfile profile = candidates.get(0).covariateProfile();
            assertThat(profile).isNotNull();

            // Verify time_of_day was parsed as StringValue
            CovariateValue timeOfDay = profile.get("time_of_day");
            assertThat(timeOfDay).isNotNull();
            assertThat(timeOfDay).isInstanceOf(CovariateValue.StringValue.class);
            assertThat(timeOfDay.toCanonicalString()).isEqualTo("08:00/2h");

            // Verify day_of_week
            CovariateValue dayOfWeek = profile.get("day_of_week");
            assertThat(dayOfWeek).isNotNull();
            assertThat(dayOfWeek.toCanonicalString()).isEqualTo("WEEKDAY");
        }
    }

    // ========== Test Subjects (for future integration testing) ==========

    @UseCase(value = "UseCaseWithCovariates", covariateTimeOfDay = {"08:00/4h"})
    static class UseCaseWithCovariates {
    }

    @UseCase("UseCaseWithoutCovariates")
    static class UseCaseWithoutCovariates {
    }

    static class TestSubjects {
        // Note: Using minPassRate to avoid spec lookup (these are unit tests, not integration tests)
        @ProbabilisticTest(samples = 10, minPassRate = 0.95, useCase = UseCaseWithCovariates.class,
                intent = TestIntent.SMOKE)
        public void testWithCovariateUseCase() {
        }

        @ProbabilisticTest(samples = 10, minPassRate = 0.95, useCase = UseCaseWithoutCovariates.class,
                intent = TestIntent.SMOKE)
        public void testWithoutCovariateUseCase() {
        }
    }

    // ========== Helper Methods ==========

    private void writeSpec(String filename, String useCaseId, String footprint, String dayOfWeekValue)
            throws IOException {
        String content = createSpecContent(useCaseId, footprint, dayOfWeekValue);
        Files.writeString(tempDir.resolve(filename), content);
    }

    private String createSpecContent(String useCaseId, String footprint, String dayOfWeekValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("footprint: ").append(footprint).append("\n");
        sb.append("covariates:\n");
        sb.append("  day_of_week: \"").append(dayOfWeekValue).append("\"\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 100\n");
        sb.append("  samplesExecuted: 100\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.95\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [0.93, 0.97]\n");
        sb.append("  successes: 95\n");
        sb.append("  failures: 5\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 1000\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: 10000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("empiricalBasis:\n");
        sb.append("  samples: 100\n");
        sb.append("  successes: 95\n");
        sb.append("  generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.95\n");
        sb.append("  successCriteria: \"Test criteria\"\n");

        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String createSpecContentWithTimeOfDay(String useCaseId, String footprint,
                                                   String timeOfDay, String dayOfWeekValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("footprint: ").append(footprint).append("\n");
        sb.append("covariates:\n");
        sb.append("  time_of_day: \"").append(timeOfDay).append("\"\n");
        sb.append("  day_of_week: \"").append(dayOfWeekValue).append("\"\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 100\n");
        sb.append("  samplesExecuted: 100\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.95\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [0.93, 0.97]\n");
        sb.append("  successes: 95\n");
        sb.append("  failures: 5\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 1000\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: 10000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("empiricalBasis:\n");
        sb.append("  samples: 100\n");
        sb.append("  successes: 95\n");
        sb.append("  generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.95\n");
        sb.append("  successCriteria: \"Test criteria\"\n");

        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
