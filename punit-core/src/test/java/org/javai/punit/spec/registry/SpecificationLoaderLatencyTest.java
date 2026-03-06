package org.javai.punit.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.model.LatencyBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SpecificationLoader — Latency parsing")
class SpecificationLoaderLatencyTest {

    @Nested
    @DisplayName("Top-level latency section")
    class TopLevelLatency {

        @Test
        @DisplayName("should parse latency section with all fields")
        void shouldParseLatencySection() {
            String yaml = createYamlWithLatency(100, 450, 120, 380, 620, 750, 1100, 1400);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isTrue();
            LatencyBaseline lb = spec.getLatencyBaseline();
            assertThat(lb.sampleCount()).isEqualTo(100);
            assertThat(lb.meanMs()).isEqualTo(450);
            assertThat(lb.standardDeviationMs()).isEqualTo(120);
            assertThat(lb.p50Ms()).isEqualTo(380);
            assertThat(lb.p90Ms()).isEqualTo(620);
            assertThat(lb.p95Ms()).isEqualTo(750);
            assertThat(lb.p99Ms()).isEqualTo(1100);
            assertThat(lb.maxMs()).isEqualTo(1400);
        }

        @Test
        @DisplayName("should return null latency baseline when no latency section")
        void shouldReturnNullWhenNoLatencySection() {
            String yaml = createYamlWithoutLatency();

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isFalse();
            assertThat(spec.getLatencyBaseline()).isNull();
        }
    }

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("existing spec without latency should parse correctly")
        void existingSpecWithoutLatencyShouldParseCorrectly() {
            String yaml = createYamlWithoutLatency();

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getUseCaseId()).isEqualTo("TestCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
            assertThat(spec.hasLatencyBaseline()).isFalse();
        }

        @Test
        @DisplayName("spec with latency should still parse other fields correctly")
        void specWithLatencyShouldStillParseOtherFields() {
            String yaml = createYamlWithLatency(50, 300, 80, 250, 500, 600, 900, 1200);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getUseCaseId()).isEqualTo("TestCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
            assertThat(spec.hasLatencyBaseline()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String createYamlWithLatency(int sampleCount, long mean, long stdDev,
                                          long p50, long p90, long p95, long p99, long max) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("useCaseId: TestCase\n");
        sb.append("generatedAt: 2026-02-15T10:00:00Z\n");
        sb.append("approvedAt: 2026-02-15T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 1000\n");
        sb.append("  samplesExecuted: 1000\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.9000\n");
        sb.append("    standardError: 0.0095\n");
        sb.append("    confidenceInterval95: [0.8814, 0.9186]\n");
        sb.append("  successes: 900\n");
        sb.append("  failures: 100\n");
        sb.append("\n");
        sb.append("latency:\n");
        sb.append("  sampleCount: ").append(sampleCount).append("\n");
        sb.append("  meanMs: ").append(mean).append("\n");
        sb.append("  standardDeviationMs: ").append(stdDev).append("\n");
        sb.append("  p50Ms: ").append(p50).append("\n");
        sb.append("  p90Ms: ").append(p90).append("\n");
        sb.append("  p95Ms: ").append(p95).append("\n");
        sb.append("  p99Ms: ").append(p99).append("\n");
        sb.append("  maxMs: ").append(max).append("\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 5000\n");
        sb.append("  avgTimePerSampleMs: 5\n");
        sb.append("  totalTokens: 100000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");

        String content = sb.toString();
        String fingerprint = SpecificationLoader.computeFingerprint(content);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String createYamlWithoutLatency() {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("useCaseId: TestCase\n");
        sb.append("generatedAt: 2026-02-15T10:00:00Z\n");
        sb.append("approvedAt: 2026-02-15T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 1000\n");
        sb.append("  samplesExecuted: 1000\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.9000\n");
        sb.append("    standardError: 0.0095\n");
        sb.append("    confidenceInterval95: [0.8814, 0.9186]\n");
        sb.append("  successes: 900\n");
        sb.append("  failures: 100\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 5000\n");
        sb.append("  avgTimePerSampleMs: 5\n");
        sb.append("  totalTokens: 100000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");

        String content = sb.toString();
        String fingerprint = SpecificationLoader.computeFingerprint(content);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }
}
