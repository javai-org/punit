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

@DisplayName("BaselineReader")
class BaselineReaderTest {

    private final BaselineReader reader = new BaselineReader();
    private final BaselineWriter writer = new BaselineWriter();

    private BaselineRecord roundTrip(Map<String, BaselineStatistics> entries) {
        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc123", 1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                entries);
        return reader.parse(writer.toYaml(original));
    }

    @Test
    @DisplayName("round-trips a PassRateStatistics record without loss")
    void roundTripPassRate() {
        BaselineRecord parsed = roundTrip(Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)));

        assertThat(parsed.useCaseId()).isEqualTo("ShoppingBasket");
        assertThat(parsed.methodName()).isEqualTo("measureBaseline");
        assertThat(parsed.factorsFingerprint()).isEqualTo("a1b2c3d4");
        assertThat(parsed.inputsIdentity()).isEqualTo("sha256:abc123");
        assertThat(parsed.sampleCount()).isEqualTo(1000);
        assertThat(parsed.generatedAt()).isEqualTo(Instant.parse("2026-04-26T15:30:00Z"));

        BaselineStatistics entry = parsed.statisticsByCriterionName().get("bernoulli-pass-rate");
        assertThat(entry).isInstanceOf(PassRateStatistics.class);
        PassRateStatistics passRate = (PassRateStatistics) entry;
        assertThat(passRate.observedPassRate()).isEqualTo(0.94);
        assertThat(passRate.sampleCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("round-trips a LatencyStatistics record (all four percentiles)")
    void roundTripLatency() {
        LatencyResult percentiles = new LatencyResult(
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofMillis(800),
                Duration.ofMillis(1200),
                1000);

        BaselineRecord parsed = roundTrip(Map.of(
                "percentile-latency", new LatencyStatistics(percentiles, 1000)));

        BaselineStatistics entry = parsed.statisticsByCriterionName().get("percentile-latency");
        assertThat(entry).isInstanceOf(LatencyStatistics.class);
        LatencyStatistics latency = (LatencyStatistics) entry;
        assertThat(latency.percentiles().p50()).isEqualTo(Duration.ofMillis(250));
        assertThat(latency.percentiles().p90()).isEqualTo(Duration.ofMillis(500));
        assertThat(latency.percentiles().p95()).isEqualTo(Duration.ofMillis(800));
        assertThat(latency.percentiles().p99()).isEqualTo(Duration.ofMillis(1200));
        assertThat(latency.sampleCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("round-trips multiple criterion entries side-by-side")
    void roundTripBothCriteria() {
        Map<String, BaselineStatistics> entries = new LinkedHashMap<>();
        entries.put("bernoulli-pass-rate", new PassRateStatistics(0.94, 1000));
        entries.put("percentile-latency", new LatencyStatistics(LatencyResult.empty(), 1000));

        BaselineRecord parsed = roundTrip(entries);

        assertThat(parsed.statisticsByCriterionName()).containsOnlyKeys(
                "bernoulli-pass-rate", "percentile-latency");
    }

    @Test
    @DisplayName("read(Path) wraps parse failure with the file path in the diagnostic")
    void readWrapsErrorWithPath(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("malformed.yaml");
        Files.writeString(file, "schemaVersion: punit-baseline-2\n");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.read(file))
                .withMessageContaining("malformed.yaml")
                .withMessageContaining("Missing required field");
    }

    @Test
    @DisplayName("rejects unknown schemaVersion")
    void rejectsUnknownSchemaVersion() {
        String yaml = "schemaVersion: punit-baseline-99\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    observedPassRate: 0.5\n"
                + "    sampleCount: 1\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("punit-baseline-99")
                .withMessageContaining("punit-baseline-2");
    }

    @Test
    @DisplayName("rejects an empty file")
    void rejectsEmptyFile() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(""))
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("rejects a missing required field with the field name in the diagnostic")
    void rejectsMissingField() {
        String yaml = "schemaVersion: punit-baseline-2\n"
                + "useCaseId: x\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("methodName");
    }

    @Test
    @DisplayName("rejects a statistics entry whose shape matches no known statistics kind")
    void rejectsUnknownStatisticsShape() {
        String yaml = "schemaVersion: punit-baseline-2\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  weird-criterion:\n"
                + "    foo: 1\n"
                + "    bar: 2\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("weird-criterion")
                .withMessageContaining("Unrecognised statistics entry shape");
    }

    @Test
    @DisplayName("round-trips a baseline with a covariate profile, preserving order")
    void roundTripCovariates() {
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("day_of_week", "WEEKDAY");
        profile.put("region", "DE_FR");
        profile.put("model_version", "v1");

        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc123", 1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)),
                org.javai.punit.api.typed.covariate.CovariateProfile.of(profile));

        BaselineRecord parsed = reader.parse(writer.toYaml(original));

        assertThat(parsed.covariateProfile().values())
                .containsExactly(
                        Map.entry("day_of_week", "WEEKDAY"),
                        Map.entry("region", "DE_FR"),
                        Map.entry("model_version", "v1"));
    }

    @Test
    @DisplayName("legacy baselines without a covariates block parse to an empty profile")
    void legacyMissingCovariates() {
        // A YAML emitted before CV-3a has no covariates: block. The
        // reader must accept it and assign the empty profile, so old
        // baselines on disk continue to work after the upgrade.
        String yaml = "schemaVersion: punit-baseline-2\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    observedPassRate: 0.5\n"
                + "    sampleCount: 1\n";

        BaselineRecord parsed = reader.parse(yaml);
        assertThat(parsed.covariateProfile().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("rejects a non-string covariate value")
    void rejectsNonStringCovariate() {
        String yaml = "schemaVersion: punit-baseline-2\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    observedPassRate: 0.5\n"
                + "    sampleCount: 1\n"
                + "covariates:\n"
                + "  region: 42\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("region")
                .withMessageContaining("must be a string");
    }
}
