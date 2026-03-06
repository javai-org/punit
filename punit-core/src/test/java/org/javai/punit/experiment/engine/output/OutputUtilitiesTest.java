package org.javai.punit.experiment.engine.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.engine.output.OutputUtilities.OutputHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OutputUtilities")
class OutputUtilitiesTest {

    @Nested
    @DisplayName("computeFingerprint()")
    class ComputeFingerprint {

        @Test
        @DisplayName("should return 64-character hex string")
        void shouldReturn64CharacterHexString() {
            String fingerprint = OutputUtilities.computeFingerprint("test content");

            assertThat(fingerprint)
                .hasSize(64)
                .matches("[a-f0-9]+");
        }

        @Test
        @DisplayName("should be deterministic for same input")
        void shouldBeDeterministicForSameInput() {
            String content = "some content";

            String fp1 = OutputUtilities.computeFingerprint(content);
            String fp2 = OutputUtilities.computeFingerprint(content);

            assertThat(fp1).isEqualTo(fp2);
        }

        @Test
        @DisplayName("should produce different fingerprints for different input")
        void shouldProduceDifferentFingerprintsForDifferentInput() {
            String fp1 = OutputUtilities.computeFingerprint("content A");
            String fp2 = OutputUtilities.computeFingerprint("content B");

            assertThat(fp1).isNotEqualTo(fp2);
        }
    }

    @Nested
    @DisplayName("appendFingerprint()")
    class AppendFingerprint {

        @Test
        @DisplayName("should append fingerprint line to content")
        void shouldAppendFingerprintLineToContent() {
            String content = "field: value\n";

            String result = OutputUtilities.appendFingerprint(content);

            assertThat(result)
                .startsWith(content)
                .contains("contentFingerprint:")
                .endsWith("\n");
        }

        @Test
        @DisplayName("fingerprint should match content hash")
        void fingerprintShouldMatchContentHash() {
            String content = "field: value\n";

            String result = OutputUtilities.appendFingerprint(content);
            String expectedFingerprint = OutputUtilities.computeFingerprint(content);

            assertThat(result).contains("contentFingerprint: " + expectedFingerprint);
        }
    }

    @Nested
    @DisplayName("writeHeader()")
    class WriteHeader {

        @Test
        @DisplayName("should write baseline header with all fields")
        void shouldWriteBaselineHeaderWithAllFields() {
            YamlBuilder builder = YamlBuilder.create();
            OutputHeader header = OutputHeader.forBaseline(
                "TestUseCase",
                "experiment-v1",
                Instant.parse("2026-02-02T10:00:00Z"),
                "com.example.TestExperiment",
                "measureBaseline"
            );

            OutputUtilities.writeHeader(builder, header);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("# Empirical Baseline for TestUseCase")
                .contains("schemaVersion: punit-spec-1")
                .contains("useCaseId: TestUseCase")
                .contains("experimentId: experiment-v1")
                .contains("generatedAt: 2026-02-02T10:00:00Z")
                .contains("experimentClass: com.example.TestExperiment")
                .contains("experimentMethod: measureBaseline");
        }

        @Test
        @DisplayName("should write optimization header")
        void shouldWriteOptimizationHeader() {
            YamlBuilder builder = YamlBuilder.create();
            OutputHeader header = OutputHeader.forOptimization(
                "TestUseCase",
                "optimize-v1",
                Instant.parse("2026-02-02T10:00:00Z")
            );

            OutputUtilities.writeHeader(builder, header);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("# Optimization History for TestUseCase")
                .contains("schemaVersion: punit-optimize-1")
                .contains("useCaseId: TestUseCase")
                .contains("experimentId: optimize-v1")
                .doesNotContain("experimentClass")
                .doesNotContain("experimentMethod");
        }

        @Test
        @DisplayName("should omit optional fields when null")
        void shouldOmitOptionalFieldsWhenNull() {
            YamlBuilder builder = YamlBuilder.create();
            OutputHeader header = OutputHeader.forBaseline(
                "TestUseCase",
                null,  // no experimentId
                Instant.parse("2026-02-02T10:00:00Z"),
                null,  // no experimentClass
                null   // no experimentMethod
            );

            OutputUtilities.writeHeader(builder, header);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("useCaseId: TestUseCase")
                .doesNotContain("experimentId")
                .doesNotContain("experimentClass")
                .doesNotContain("experimentMethod");
        }
    }
}
