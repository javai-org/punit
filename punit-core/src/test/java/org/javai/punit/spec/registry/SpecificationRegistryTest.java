package org.javai.punit.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SpecificationRegistry")
class SpecificationRegistryTest {

    @TempDir
    Path tempDir;

    private SpecificationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SpecificationRegistry(tempDir);
    }

    private void createSpecFile(String useCaseId, double minPassRate) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("specId: ").append(useCaseId).append("\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
        sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 100\n");
        sb.append("  samplesExecuted: 100\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: ").append(minPassRate).append("\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [").append(minPassRate - 0.02).append(", ").append(minPassRate + 0.02).append("]\n");
        sb.append("  successes: ").append((int)(100 * minPassRate)).append("\n");
        sb.append("  failures: ").append((int)(100 * (1 - minPassRate))).append("\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 1000\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: 10000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: ").append(minPassRate).append("\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
        
        Files.writeString(tempDir.resolve(useCaseId + ".yaml"), sb.toString());
    }

    private void createV2SpecFile(String useCaseId, int samples, int successes) throws IOException {
        double successRate = (double) successes / samples;
        int failures = samples - successes;
        
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("specId: ").append(useCaseId).append("\n");
        sb.append("useCaseId: ").append(useCaseId).append("\n");
        sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: ").append(samples).append("\n");
        sb.append("  samplesExecuted: ").append(samples).append("\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: ").append(String.format("%.4f", successRate)).append("\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [").append(String.format("%.4f", successRate - 0.02)).append(", ").append(String.format("%.4f", successRate + 0.02)).append("]\n");
        sb.append("  successes: ").append(successes).append("\n");
        sb.append("  failures: ").append(failures).append("\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: ").append(samples * 10).append("\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: ").append(samples * 100).append("\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("empiricalBasis:\n");
        sb.append("  samples: ").append(samples).append("\n");
        sb.append("  successes: ").append(successes).append("\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

        Files.writeString(tempDir.resolve(useCaseId + ".yaml"), sb.toString());
    }

    private String computeFingerprint(String content) {
        return SpecificationLoader.computeFingerprint(content);
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("loads spec by use case ID")
        void loadsSpecByUseCaseId() throws IOException {
            createSpecFile("TestUseCase", 0.85);

            var spec = registry.resolve("TestUseCase");

            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("loads v2 spec with empirical basis")
        void loadsV2SpecWithEmpiricalBasis() throws IOException {
            createV2SpecFile("TestUseCase", 1000, 900);

            var spec = registry.resolve("TestUseCase");

            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
            assertThat(spec.hasEmpiricalBasis()).isTrue();
            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(1000);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(900);
            assertThat(spec.getObservedRate()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("strips legacy version suffix")
        void stripsLegacyVersionSuffix() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            var spec = registry.resolve("TestUseCase:v1");

            assertThat(spec.getUseCaseId()).isEqualTo("TestUseCase");
        }

        @Test
        @DisplayName("caches loaded specs")
        void cachesLoadedSpecs() throws IOException {
            createSpecFile("TestUseCase", 0.85);

            var spec1 = registry.resolve("TestUseCase");
            var spec2 = registry.resolve("TestUseCase");

            assertThat(spec1).isSameAs(spec2);
        }

        @Test
        @DisplayName("throws when spec not found")
        void throwsWhenSpecNotFound() {
            assertThatThrownBy(() -> registry.resolve("NonExistent"))
                .isInstanceOf(SpecificationNotFoundException.class)
                .hasMessageContaining("NonExistent");
        }

        @Test
        @DisplayName("throws on null spec ID")
        void throwsOnNullSpecId() {
            assertThatThrownBy(() -> registry.resolve(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("finds .yml files")
        void findsYmlFiles() throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-1\n");
            sb.append("specId: YmlCase\n");
            sb.append("useCaseId: YmlCase\n");
            sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
            sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
            sb.append("approvedBy: tester\n");
            sb.append("\n");
            sb.append("execution:\n");
            sb.append("  samplesPlanned: 100\n");
            sb.append("  samplesExecuted: 100\n");
            sb.append("  terminationReason: COMPLETED\n");
            sb.append("\n");
            sb.append("statistics:\n");
            sb.append("  successRate:\n");
            sb.append("    observed: 0.8\n");
            sb.append("    standardError: 0.04\n");
            sb.append("    confidenceInterval95: [0.72, 0.88]\n");
            sb.append("  successes: 80\n");
            sb.append("  failures: 20\n");
            sb.append("\n");
            sb.append("cost:\n");
            sb.append("  totalTimeMs: 1000\n");
            sb.append("  avgTimePerSampleMs: 10\n");
            sb.append("  totalTokens: 10000\n");
            sb.append("  avgTokensPerSample: 100\n");
            sb.append("\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.8\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            Files.writeString(tempDir.resolve("YmlCase.yml"), sb.toString());

            var spec = registry.resolve("YmlCase");

            assertThat(spec.getUseCaseId()).isEqualTo("YmlCase");
        }

        @Test
        @DisplayName("validates v2 spec without approval metadata")
        void validatesV2SpecWithoutApprovalMetadata() throws IOException {
            createV2SpecFile("TestUseCase", 500, 450);

            var spec = registry.resolve("TestUseCase");

            // v2 specs without approval should still be valid
            assertThat(spec.hasApprovalMetadata()).isFalse();
            // validate() should not throw
            spec.validate();
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true for existing spec")
        void returnsTrueForExistingSpec() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            assertThat(registry.exists("TestUseCase")).isTrue();
        }

        @Test
        @DisplayName("returns true for existing v2 spec")
        void returnsTrueForExistingV2Spec() throws IOException {
            createV2SpecFile("TestUseCase", 100, 90);

            assertThat(registry.exists("TestUseCase")).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existing spec")
        void returnsFalseForNonExistingSpec() {
            assertThat(registry.exists("NonExistent")).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(registry.exists(null)).isFalse();
        }

        @Test
        @DisplayName("returns true for cached spec")
        void returnsTrueForCachedSpec() throws IOException {
            createSpecFile("TestUseCase", 0.9);
            registry.resolve("TestUseCase"); // Load into cache

            assertThat(registry.exists("TestUseCase")).isTrue();
        }

        @Test
        @DisplayName("handles legacy version suffix")
        void handlesLegacyVersionSuffix() throws IOException {
            createSpecFile("TestUseCase", 0.9);

            assertThat(registry.exists("TestUseCase:v1")).isTrue();
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCache {

        @Test
        @DisplayName("clears cached specs")
        void clearsCachedSpecs() throws IOException {
            createSpecFile("TestUseCase", 0.85);
            var spec1 = registry.resolve("TestUseCase");
            
            registry.clearCache();
            var spec2 = registry.resolve("TestUseCase");

            assertThat(spec1).isNotSameAs(spec2);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("throws on null specs root")
        void throwsOnNullSpecsRoot() {
            assertThatThrownBy(() -> new SpecificationRegistry(null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
