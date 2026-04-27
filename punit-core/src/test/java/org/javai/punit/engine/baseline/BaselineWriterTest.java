package org.javai.punit.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.LatencyStatistics;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

@DisplayName("BaselineWriter — punit-baseline-2 schema serialisation")
class BaselineWriterTest {

    private final BaselineWriter writer = new BaselineWriter();

    private BaselineRecord recordWith(Map<String, BaselineStatistics> stats) {
        return new BaselineRecord(
                "ShoppingBasketUseCase",
                "measureBaseline",
                "a1b2c3d4",
                "sha256:7d3a8c1e9b2f",
                1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                stats);
    }

    @Test
    @DisplayName("emits the schema discriminator and identity keys")
    void emitsHeader() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000))));

        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root)
                .containsEntry("schemaVersion", "punit-baseline-2")
                .containsEntry("useCaseId", "ShoppingBasketUseCase")
                .containsEntry("methodName", "measureBaseline")
                .containsEntry("factorsFingerprint", "a1b2c3d4")
                .containsEntry("inputsIdentity", "sha256:7d3a8c1e9b2f")
                .containsEntry("sampleCount", 1000)
                .containsEntry("generatedAt", "2026-04-26T15:30:00Z");
    }

    @Test
    @DisplayName("serialises a PassRateStatistics entry with observedPassRate + sampleCount")
    void serialisesPassRate() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000))));

        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");
        Map<String, Object> entry = (Map<String, Object>) stats.get("bernoulli-pass-rate");

        assertThat(entry)
                .containsEntry("observedPassRate", 0.94)
                .containsEntry("sampleCount", 1000);
    }

    @Test
    @DisplayName("serialises a LatencyStatistics entry with all four percentiles in ISO-8601")
    void serialisesLatency() {
        LatencyResult percentiles = new LatencyResult(
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofMillis(800),
                Duration.ofMillis(1200),
                1000);

        String yaml = writer.toYaml(recordWith(Map.of(
                "percentile-latency", new LatencyStatistics(percentiles, 1000))));

        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");
        Map<String, Object> entry = (Map<String, Object>) stats.get("percentile-latency");

        assertThat(entry).containsEntry("sampleCount", 1000);

        Map<String, Object> percentileMap = (Map<String, Object>) entry.get("percentiles");
        assertThat(percentileMap)
                .containsEntry("p50", "PT0.25S")
                .containsEntry("p90", "PT0.5S")
                .containsEntry("p95", "PT0.8S")
                .containsEntry("p99", "PT1.2S");
    }

    @Test
    @DisplayName("serialises multiple criterion entries side-by-side")
    void serialisesBothCriteria() {
        Map<String, BaselineStatistics> entries = new LinkedHashMap<>();
        entries.put("bernoulli-pass-rate", new PassRateStatistics(0.94, 1000));
        entries.put("percentile-latency", new LatencyStatistics(LatencyResult.empty(), 1000));

        String yaml = writer.toYaml(recordWith(entries));
        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");

        assertThat(stats).containsKeys("bernoulli-pass-rate", "percentile-latency");
    }

    @Test
    @DisplayName("write(...) creates baselineDir if missing and returns the file path")
    void writeCreatesDirectory(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");

        Path file = writer.write(
                recordWith(Map.of("bernoulli-pass-rate", new PassRateStatistics(0.5, 100))),
                baselineDir);

        assertThat(Files.exists(baselineDir)).isTrue();
        assertThat(file)
                .exists()
                .isEqualTo(baselineDir.resolve(
                        "ShoppingBasketUseCase.measureBaseline-a1b2c3d4.yaml"));

        String content = Files.readString(file);
        assertThat(content).contains("schemaVersion: punit-baseline-2");
    }

    @Test
    @DisplayName("rejects an unsupported BaselineStatistics flavour with a diagnostic")
    void rejectsUnknownStatisticsKind() {
        BaselineStatistics unknown = () -> 1;
        BaselineRecord record = recordWith(Map.of("custom-criterion", unknown));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> writer.toYaml(record))
                .withMessageContaining("custom-criterion")
                .withMessageContaining("Unsupported baseline statistics kind");
    }
}
