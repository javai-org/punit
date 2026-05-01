package org.javai.punit.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("YamlBaselineProvider — bridges BaselineProvider contract to file-system resolver")
class YamlBaselineProviderTest {

    record Factors(String model, double temperature) { }

    private final BaselineWriter writer = new BaselineWriter();

    @Test
    @DisplayName("returns the recorded statistics when use case + factors fingerprint match")
    void resolvesPresent(@TempDir Path dir) throws IOException {
        FactorBundle bundle = FactorBundle.of(new Factors("gpt-4o", 0.0));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(bundle),
                Map.of("bernoulli-pass-rate", new PassRateStatistics(0.92, 1000)));
        writer.write(record, dir);

        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket", bundle, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().observedPassRate()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("returns empty for an unmatched factor bundle (different fingerprint)")
    void emptyForDifferentFactors(@TempDir Path dir) throws IOException {
        FactorBundle measured = FactorBundle.of(new Factors("gpt-4o", 0.0));
        FactorBundle queried = FactorBundle.of(new Factors("gpt-4o", 0.7));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(measured),
                Map.of("bernoulli-pass-rate", new PassRateStatistics(0.92, 1000)));
        writer.write(record, dir);

        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket", queried, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("returns empty for an empty baseline directory")
    void emptyForNoBaselines(@TempDir Path dir) {
        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket",
                FactorBundle.of(new Factors("gpt-4o", 0.0)),
                "bernoulli-pass-rate",
                PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    private BaselineRecord baseline(String useCaseId, String fingerprint,
                                     Map<String, BaselineStatistics> entries) {
        return new BaselineRecord(
                useCaseId, "measureBaseline", fingerprint,
                "sha256:irrelevant", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                entries);
    }
}
