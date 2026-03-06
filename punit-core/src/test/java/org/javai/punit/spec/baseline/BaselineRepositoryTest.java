package org.javai.punit.spec.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BaselineRepository}.
 */
@DisplayName("BaselineRepository")
class BaselineRepositoryTest {

    @TempDir
    Path tempDir;

    private BaselineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new BaselineRepository(tempDir);
    }

    @Nested
    @DisplayName("findCandidates()")
    class FindCandidatesTests {

        @Test
        @DisplayName("should return empty when directory is empty")
        void findCandidates_returnsEmptyWhenDirectoryEmpty() {
            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("should return empty when no matching use case ID")
        void findCandidates_returnsEmptyWhenNoMatchingUseCaseId() throws IOException {
            writeSpec("OtherUseCase.yaml", "OtherUseCase", "abc123", "WEEKDAY");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("should find matching footprint")
        void findCandidates_findsMatchingFootprint() throws IOException {
            writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");
            writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "WEEKEND");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).footprint()).isEqualTo("abc123");
        }

        @Test
        @DisplayName("should exclude different footprint")
        void findCandidates_excludesDifferentFootprint() throws IOException {
            writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "WEEKEND");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("should populate covariate profile")
        void findCandidates_populatesCovariateProfile() throws IOException {
            writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
            CovariateProfile profile = candidates.get(0).covariateProfile();
            assertThat(profile).isNotNull();
            assertThat(profile.get("day_of_week").toCanonicalString()).isEqualTo("WEEKDAY");
        }

        @Test
        @DisplayName("should load spec correctly")
        void findCandidates_loadsSpecCorrectly() throws IOException {
            writeSpec("TestUseCase.yaml", "TestUseCase", "abc123", "WEEKDAY");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
            ExecutionSpecification spec = candidates.get(0).spec();
            assertThat(spec).isNotNull();
            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
        }

        @Test
        @DisplayName("should match simple filename")
        void findCandidates_matchesSimpleFilename() throws IOException {
            writeSpec("TestUseCase.yaml", "TestUseCase", "abc123", "WEEKDAY");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
            assertThat(candidates.get(0).filename()).isEqualTo("TestUseCase.yaml");
        }

        @Test
        @DisplayName("should match filename with hashes")
        void findCandidates_matchesFilenameWithHashes() throws IOException {
            writeSpec("TestUseCase-abc1-cov1-cov2.yaml", "TestUseCase", "abc123", "WEEKDAY");

            List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
        }

        @Test
        @DisplayName("should handle non-existent directory")
        void findCandidates_handlesNonExistentDirectory() {
            Path nonExistent = tempDir.resolve("nonexistent");
            BaselineRepository repo = new BaselineRepository(nonExistent);

            List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");
            assertThat(candidates).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllCandidates()")
    class FindAllCandidatesTests {

        @Test
        @DisplayName("should return all footprints")
        void findAllCandidates_returnsAllFootprints() throws IOException {
            writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");
            writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "WEEKEND");

            List<BaselineCandidate> candidates = repository.findAllCandidates("TestUseCase");

            assertThat(candidates).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findAvailableFootprints()")
    class FindAvailableFootprintsTests {

        @Test
        @DisplayName("should return distinct footprints")
        void findAvailableFootprints_returnsDistinctFootprints() throws IOException {
            writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");
            writeSpec("TestUseCase-abc1-cov1.yaml", "TestUseCase", "abc123", "WEEKEND");
            writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "WEEKDAY");

            List<String> footprints = repository.findAvailableFootprints("TestUseCase");

            assertThat(footprints).hasSize(2);
            assertThat(footprints).contains("abc123", "def456");
        }
    }

    @Nested
    @DisplayName("getSpecsRoot()")
    class GetSpecsRootTests {

        @Test
        @DisplayName("should return configured path")
        void getSpecsRoot_returnsConfiguredPath() {
            assertThat(repository.getSpecsRoot()).isEqualTo(tempDir);
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

        // Compute fingerprint (content before fingerprint line)
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
