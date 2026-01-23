package org.javai.punit.spec.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BaselineRepository}.
 */
class BaselineRepositoryTest {

    @TempDir
    Path tempDir;

    private BaselineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new BaselineRepository(tempDir);
    }

    @Test
    void findCandidates_returnsEmptyWhenDirectoryEmpty() {
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        assertTrue(candidates.isEmpty());
    }

    @Test
    void findCandidates_returnsEmptyWhenNoMatchingUseCaseId() throws IOException {
        writeSpec("OtherUseCase.yaml", "OtherUseCase", "abc123", "weekday");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        assertTrue(candidates.isEmpty());
    }

    @Test
    void findCandidates_findsMatchingFootprint() throws IOException {
        writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "weekday");
        writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "weekend");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        
        assertEquals(1, candidates.size());
        assertEquals("abc123", candidates.get(0).footprint());
    }

    @Test
    void findCandidates_excludesDifferentFootprint() throws IOException {
        writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "weekend");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        assertTrue(candidates.isEmpty());
    }

    @Test
    void findAllCandidates_returnsAllFootprints() throws IOException {
        writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "weekday");
        writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "weekend");
        
        List<BaselineCandidate> candidates = repository.findAllCandidates("TestUseCase");
        
        assertEquals(2, candidates.size());
    }

    @Test
    void findCandidates_populatesCovariateProfile() throws IOException {
        writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "weekday");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        
        assertEquals(1, candidates.size());
        CovariateProfile profile = candidates.get(0).covariateProfile();
        assertNotNull(profile);
        assertEquals("weekday", profile.get("weekday_vs_weekend").toCanonicalString());
    }

    @Test
    void findCandidates_loadsSpecCorrectly() throws IOException {
        writeSpec("TestUseCase.yaml", "TestUseCase", "abc123", "weekday");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        
        assertEquals(1, candidates.size());
        ExecutionSpecification spec = candidates.get(0).spec();
        assertNotNull(spec);
        assertEquals("TestUseCase", spec.getUseCaseId());
    }

    @Test
    void findAvailableFootprints_returnsDistinctFootprints() throws IOException {
        writeSpec("TestUseCase-abc1.yaml", "TestUseCase", "abc123", "weekday");
        writeSpec("TestUseCase-abc1-cov1.yaml", "TestUseCase", "abc123", "weekend");
        writeSpec("TestUseCase-def4.yaml", "TestUseCase", "def456", "weekday");
        
        List<String> footprints = repository.findAvailableFootprints("TestUseCase");
        
        assertEquals(2, footprints.size());
        assertTrue(footprints.contains("abc123"));
        assertTrue(footprints.contains("def456"));
    }

    @Test
    void findCandidates_matchesSimpleFilename() throws IOException {
        writeSpec("TestUseCase.yaml", "TestUseCase", "abc123", "weekday");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        
        assertEquals(1, candidates.size());
        assertEquals("TestUseCase.yaml", candidates.get(0).filename());
    }

    @Test
    void findCandidates_matchesFilenameWithHashes() throws IOException {
        writeSpec("TestUseCase-abc1-cov1-cov2.yaml", "TestUseCase", "abc123", "weekday");
        
        List<BaselineCandidate> candidates = repository.findCandidates("TestUseCase", "abc123");
        
        assertEquals(1, candidates.size());
    }

    @Test
    void findCandidates_handlesNonExistentDirectory() {
        Path nonExistent = tempDir.resolve("nonexistent");
        BaselineRepository repo = new BaselineRepository(nonExistent);
        
        List<BaselineCandidate> candidates = repo.findCandidates("TestUseCase", "abc123");
        assertTrue(candidates.isEmpty());
    }

    @Test
    void getSpecsRoot_returnsConfiguredPath() {
        assertEquals(tempDir, repository.getSpecsRoot());
    }

    // Helper methods

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
        
        // Compute fingerprint (content before fingerprint line)
        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");
        
        return sb.toString();
    }

    private String computeFingerprint(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

