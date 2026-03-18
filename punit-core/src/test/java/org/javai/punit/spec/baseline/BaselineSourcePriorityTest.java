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
import java.util.Map;

import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineSource;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.SelectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies env-local source priority across the full
 * {@link BaselineRepository} → {@link BaselineSelector} pipeline.
 */
@DisplayName("BaselineSourcePriority")
class BaselineSourcePriorityTest {

    @TempDir
    Path envLocalDir;

    @TempDir
    Path bundledDir;

    private final BaselineSelector selector = new BaselineSelector();

    @Nested
    @DisplayName("covariate-free specs")
    class CovariateFreeTests {

        @Test
        @DisplayName("env-local wins when both dirs have identical specs")
        void envLocalWinsForCovariateFreeSpecs() throws IOException {
            writeSpec(envLocalDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");
            writeSpec(bundledDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");

            BaselineRepository repo = new BaselineRepository(envLocalDir, bundledDir);
            List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(2);

            SelectionResult result = selector.select(candidates, CovariateProfile.empty(), CovariateDeclaration.EMPTY);

            assertThat(result.hasSelection()).isTrue();
            assertThat(result.selected().source()).isEqualTo(BaselineSource.ENVIRONMENT_LOCAL);
        }
    }

    @Nested
    @DisplayName("covariate-bearing specs")
    class CovariateBearingTests {

        @Test
        @DisplayName("env-local wins when both dirs have matching covariate profiles")
        void envLocalWinsForCovariateBearingSpecs() throws IOException {
            writeSpec(envLocalDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");
            writeSpec(bundledDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY");

            BaselineRepository repo = new BaselineRepository(envLocalDir, bundledDir);
            List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");

            CovariateProfile testProfile = CovariateProfile.builder()
                    .put("day_of_week", "WEEKDAY")
                    .build();

            CovariateDeclaration declaration = new CovariateDeclaration(
                    List.of(), List.of(), List.of(), false, Map.of(), UseCaseAttributes.DEFAULT);

            SelectionResult result = selector.select(candidates, testProfile, declaration);

            assertThat(result.hasSelection()).isTrue();
            assertThat(result.selected().source()).isEqualTo(BaselineSource.ENVIRONMENT_LOCAL);
        }
    }

    @Nested
    @DisplayName("warmup-bearing specs")
    class WarmupBearingTests {

        @Test
        @DisplayName("env-local wins when both dirs have specs with warmup")
        void envLocalWinsForWarmupBearingSpecs() throws IOException {
            writeSpec(envLocalDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY", 5);
            writeSpec(bundledDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY", 5);

            BaselineRepository repo = new BaselineRepository(envLocalDir, bundledDir);
            List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");

            CovariateProfile testProfile = CovariateProfile.builder()
                    .put("warmup", "5")
                    .put("day_of_week", "WEEKDAY")
                    .build();

            CovariateDeclaration declaration = new CovariateDeclaration(
                    List.of(), List.of(), List.of(), false, Map.of(), new UseCaseAttributes(5));

            SelectionResult result = selector.select(candidates, testProfile, declaration);

            assertThat(result.hasSelection()).isTrue();
            assertThat(result.selected().source()).isEqualTo(BaselineSource.ENVIRONMENT_LOCAL);
        }

        @Test
        @DisplayName("warmup value is read from execution section of spec YAML")
        void warmupValueIsReadFromSpec() throws IOException {
            writeSpec(envLocalDir, "TestUseCase-abc1.yaml", "TestUseCase", "abc123", "WEEKDAY", 5);

            BaselineRepository repo = new BaselineRepository(envLocalDir, bundledDir);
            List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");

            assertThat(candidates).hasSize(1);
            assertThat(candidates.getFirst().spec().getWarmup()).isEqualTo(5);
        }
    }

    private void writeSpec(Path dir, String filename, String useCaseId, String footprint,
                           String dayOfWeekValue) throws IOException {
        writeSpec(dir, filename, useCaseId, footprint, dayOfWeekValue, 0);
    }

    private void writeSpec(Path dir, String filename, String useCaseId, String footprint,
                           String dayOfWeekValue, int warmup) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("footprint: ").append(footprint).append("\n");
        sb.append("covariates:\n");
        sb.append("  day_of_week: \"").append(dayOfWeekValue).append("\"\n");
        sb.append("\n");
        sb.append("execution:\n");
        if (warmup > 0) {
            sb.append("  warmup: ").append(warmup).append("\n");
        }
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

        Files.writeString(dir.resolve(filename), sb.toString());
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
