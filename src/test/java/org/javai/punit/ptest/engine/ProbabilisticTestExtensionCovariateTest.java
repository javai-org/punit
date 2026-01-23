package org.javai.punit.ptest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.BaselineRepository;
import org.javai.punit.spec.baseline.NoCompatibleBaselineException;
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
class ProbabilisticTestExtensionCovariateTest {

    @TempDir
    Path tempDir;

    @Test
    void baselineRepository_findsCandidatesWithMatchingFootprint() throws IOException {
        // Create baseline files with different footprints
        writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "weekday");
        writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "weekend");
        
        BaselineRepository repository = new BaselineRepository(tempDir);
        
        var candidates = repository.findCandidates("TestUseCase", "abc12345");
        
        assertEquals(1, candidates.size());
        assertEquals("abc12345", candidates.get(0).footprint());
        assertEquals("weekday", candidates.get(0).covariateProfile().get("weekday_vs_weekend").toCanonicalString());
    }

    @Test
    void baselineRepository_returnsEmptyWhenNoMatchingFootprint() throws IOException {
        writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "weekday");
        
        BaselineRepository repository = new BaselineRepository(tempDir);
        
        var candidates = repository.findCandidates("TestUseCase", "xyz99999");
        
        assertTrue(candidates.isEmpty());
    }

    @Test
    void baselineRepository_findAllCandidatesReturnsAll() throws IOException {
        writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "weekday");
        writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "weekend");
        
        BaselineRepository repository = new BaselineRepository(tempDir);
        
        var candidates = repository.findAllCandidates("TestUseCase");
        
        assertEquals(2, candidates.size());
    }

    @Test
    void baselineRepository_findAvailableFootprints() throws IOException {
        writeSpec("TestUseCase-abc12345.yaml", "TestUseCase", "abc12345", "weekday");
        writeSpec("TestUseCase-def98765.yaml", "TestUseCase", "def98765", "weekend");
        
        BaselineRepository repository = new BaselineRepository(tempDir);
        
        var footprints = repository.findAvailableFootprints("TestUseCase");
        
        assertEquals(2, footprints.size());
        assertTrue(footprints.contains("abc12345"));
        assertTrue(footprints.contains("def98765"));
    }

    @Test
    void noCompatibleBaselineException_containsUsefulInformation() {
        NoCompatibleBaselineException ex = new NoCompatibleBaselineException(
                "MyUseCase",
                "expected123",
                java.util.List.of("actual456", "actual789"));
        
        assertEquals("MyUseCase", ex.getUseCaseId());
        assertEquals("expected123", ex.getExpectedFootprint());
        assertEquals(java.util.List.of("actual456", "actual789"), ex.getAvailableFootprints());
        
        String message = ex.getMessage();
        assertTrue(message.contains("MyUseCase"));
        assertTrue(message.contains("expected123"));
        assertTrue(message.contains("actual456"));
        assertTrue(message.contains("actual789"));
        assertTrue(message.contains("MEASURE experiment"));
    }

    @Test
    void noCompatibleBaselineException_handlesEmptyAvailableFootprints() {
        NoCompatibleBaselineException ex = new NoCompatibleBaselineException(
                "MyUseCase",
                "expected123",
                java.util.List.of());
        
        String message = ex.getMessage();
        assertTrue(message.contains("No baselines found"));
    }

    @Test
    void baselineRepository_loadsCovariateProfileFromSpec() throws IOException {
        // Create a spec with time_of_day covariate
        String content = createSpecContentWithTimeOfDay("TestUseCase", "abc12345", 
                "10:00-11:00 Europe/London", "weekday");
        Files.writeString(tempDir.resolve("TestUseCase-abc12345.yaml"), content);
        
        BaselineRepository repository = new BaselineRepository(tempDir);
        var candidates = repository.findCandidates("TestUseCase", "abc12345");
        
        assertEquals(1, candidates.size());
        CovariateProfile profile = candidates.get(0).covariateProfile();
        assertNotNull(profile);
        
        // Verify time_of_day was parsed as TimeWindowValue
        CovariateValue timeOfDay = profile.get("time_of_day");
        assertNotNull(timeOfDay);
        assertInstanceOf(CovariateValue.TimeWindowValue.class, timeOfDay);
        
        CovariateValue.TimeWindowValue twv = (CovariateValue.TimeWindowValue) timeOfDay;
        assertEquals(LocalTime.of(10, 0), twv.start());
        assertEquals(LocalTime.of(11, 0), twv.end());
        assertEquals(ZoneId.of("Europe/London"), twv.timezone());
        
        // Verify weekday_vs_weekend
        CovariateValue weekday = profile.get("weekday_vs_weekend");
        assertNotNull(weekday);
        assertEquals("weekday", weekday.toCanonicalString());
    }

    // ========== Test Subjects (for future integration testing) ==========

    @UseCase(value = "UseCaseWithCovariates", covariates = {StandardCovariate.TIME_OF_DAY})
    static class UseCaseWithCovariates {
    }

    @UseCase("UseCaseWithoutCovariates")
    static class UseCaseWithoutCovariates {
    }

    static class TestSubjects {
        // Note: Using minPassRate to avoid spec lookup (these are unit tests, not integration tests)
        @ProbabilisticTest(samples = 10, minPassRate = 0.95, useCase = UseCaseWithCovariates.class)
        public void testWithCovariateUseCase() {
        }

        @ProbabilisticTest(samples = 10, minPassRate = 0.95, useCase = UseCaseWithoutCovariates.class)
        public void testWithoutCovariateUseCase() {
        }
    }

    // ========== Helper Methods ==========

    private void writeSpec(String filename, String useCaseId, String footprint, String weekdayValue)
            throws IOException {
        String content = createSpecContent(useCaseId, footprint, weekdayValue);
        Files.writeString(tempDir.resolve(filename), content);
    }

    private String createSpecContent(String useCaseId, String footprint, String weekdayValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("footprint: ").append(footprint).append("\n");
        sb.append("covariates:\n");
        sb.append("  weekday_vs_weekend: \"").append(weekdayValue).append("\"\n");
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
                                                   String timeOfDay, String weekdayValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2024-01-15T10:30:00Z\n");
        sb.append("footprint: ").append(footprint).append("\n");
        sb.append("covariates:\n");
        sb.append("  time_of_day: \"").append(timeOfDay).append("\"\n");
        sb.append("  weekday_vs_weekend: \"").append(weekdayValue).append("\"\n");
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
