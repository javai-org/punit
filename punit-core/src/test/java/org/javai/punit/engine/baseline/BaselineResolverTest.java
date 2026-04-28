package org.javai.punit.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.LatencyStatistics;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BaselineResolver — exact-match by useCaseId + factorsFingerprint")
class BaselineResolverTest {

    private final BaselineWriter writer = new BaselineWriter();

    private void writeBaseline(Path dir, String useCaseId, String fingerprint,
                                Map<String, BaselineStatistics> entries) throws IOException {
        BaselineRecord record = new BaselineRecord(
                useCaseId, "measureBaseline", fingerprint,
                "sha256:abc", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                entries);
        writer.write(record, dir);
    }

    @Test
    @DisplayName("resolves a present PassRateStatistics entry")
    void resolvesPresentEntry(@TempDir Path dir) throws IOException {
        writeBaseline(dir, "ShoppingBasket", "a1b2c3d4", Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)));

        var resolver = new BaselineResolver(dir);

        var resolved = resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().observedPassRate()).isEqualTo(0.94);
    }

    @Test
    @DisplayName("returns empty when the baseline directory does not exist")
    void emptyForMissingDirectory(@TempDir Path tempDir) {
        var resolver = new BaselineResolver(tempDir.resolve("does-not-exist"));

        var resolved = resolver.resolve(
                "X", "f", "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("returns empty when no file matches the (useCaseId, fingerprint) pair")
    void emptyForNonMatchingFile(@TempDir Path dir) throws IOException {
        writeBaseline(dir, "Other", "a1b2c3d4", Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.5, 100)));

        var resolver = new BaselineResolver(dir);

        var resolved = resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the file matches but the criterion name does not")
    void emptyForUnknownCriterion(@TempDir Path dir) throws IOException {
        writeBaseline(dir, "ShoppingBasket", "a1b2c3d4", Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)));

        var resolver = new BaselineResolver(dir);

        var resolved = resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "percentile-latency", LatencyStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("rejects mismatch between recorded statistics flavour and requested type")
    void rejectsStatisticsTypeMismatch(@TempDir Path dir) throws IOException {
        writeBaseline(dir, "ShoppingBasket", "a1b2c3d4", Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)));

        var resolver = new BaselineResolver(dir);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> resolver.resolve(
                        "ShoppingBasket", "a1b2c3d4", "bernoulli-pass-rate",
                        LatencyStatistics.class))
                .withMessageContaining("PassRateStatistics")
                .withMessageContaining("LatencyStatistics");
    }

    @Test
    @DisplayName("resolveInputsIdentity returns the recorded identity when the baseline matches")
    void resolveInputsIdentity(@TempDir Path dir) throws IOException {
        BaselineRecord record = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:specific-identity", 1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)));
        writer.write(record, dir);

        var resolver = new BaselineResolver(dir);

        assertThat(resolver.resolveInputsIdentity("ShoppingBasket", "a1b2c3d4"))
                .contains("sha256:specific-identity");
        assertThat(resolver.resolveInputsIdentity("Other", "a1b2c3d4")).isEmpty();
    }

    @Test
    @DisplayName("resolves multiple criterion entries from one baseline file")
    void resolvesMultipleEntries(@TempDir Path dir) throws IOException {
        writeBaseline(dir, "ShoppingBasket", "a1b2c3d4", new java.util.LinkedHashMap<>(Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.94, 1000),
                "percentile-latency", new LatencyStatistics(LatencyResult.empty(), 1000))));

        var resolver = new BaselineResolver(dir);

        assertThat(resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "bernoulli-pass-rate",
                PassRateStatistics.class)).isPresent();
        assertThat(resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "percentile-latency",
                LatencyStatistics.class)).isPresent();
    }

    @Test
    @DisplayName("matches a covariate-bearing filename on the (useCaseId, fingerprint) pair")
    void matchesCovariateBearingFilename(@TempDir Path dir) throws IOException {
        java.util.LinkedHashMap<String, String> profile = new java.util.LinkedHashMap<>();
        profile.put("region", "DE_FR");
        BaselineRecord record = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", new PassRateStatistics(0.94, 1000)),
                org.javai.punit.api.typed.covariate.CovariateProfile.of(profile));
        writer.write(record, dir);

        var resolver = new BaselineResolver(dir);

        assertThat(resolver.resolve(
                "ShoppingBasket", "a1b2c3d4", "bernoulli-pass-rate",
                PassRateStatistics.class)).isPresent();
    }

    @Test
    @DisplayName("does not confuse a longer fingerprint that has the requested one as a prefix")
    void doesNotConfusePrefixFingerprint(@TempDir Path dir) throws IOException {
        // -aabbccdd would match a file whose fingerprint is aabbccddee
        // if the resolver only checked startsWith — which it doesn't,
        // because the segment must be terminated by '-' or '.yaml'.
        writeBaseline(dir, "ShoppingBasket", "aabbccddee", Map.of(
                "bernoulli-pass-rate", new PassRateStatistics(0.5, 100)));

        var resolver = new BaselineResolver(dir);

        assertThat(resolver.resolve(
                "ShoppingBasket", "aabbccdd", "bernoulli-pass-rate",
                PassRateStatistics.class)).isEmpty();
    }
}
