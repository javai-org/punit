package org.javai.punit.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.javai.outcome.Outcome;
import org.javai.punit.api.typed.ContractBuilder;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.TokenTracker;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.Experiment;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PowerAnalysis.sampleSize")
class PowerAnalysisTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("m");
    private static final String USE_CASE_ID = "echo-use-case";

    private static final UseCase<Factors, String, String> ECHO = new UseCase<>() {
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public String id() { return USE_CASE_ID; }
    };

    private static Supplier<Experiment> baseline() {
        return () -> {
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .useCaseFactory(f -> ECHO)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, FACTORS).build();
        };
    }

    /**
     * Writes a baseline file matching {@link #baseline()}'s identity
     * to {@code dir} so the resolver can find it; returns {@code dir}
     * for chaining.
     */
    private static Path writeBaselineWithRate(Path dir, double passRate, int sampleCount)
            throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline", fingerprint,
                "sha256:any", sampleCount, Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PassRateStatistics(passRate, sampleCount)));
        new BaselineWriter().write(record, dir);
        return dir;
    }

    @Test
    @DisplayName("returns a positive integer when a matching baseline exists")
    void returnsPositiveInteger(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.90, 1000);

        int n = PowerAnalysis.sampleSize(dir, baseline(), 0.02, 0.80);

        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("baseline rate drives the sample-size estimate — different rates produce different n")
    void differentBaselineRatesProduceDifferentN(@TempDir Path dirA, @TempDir Path dirB)
            throws IOException {
        // p=0.5 maximises p(1-p), so it requires the largest n. p=0.9 is much
        // less variable and so requires far fewer samples for the same MDE.
        writeBaselineWithRate(dirA, 0.50, 1000);
        writeBaselineWithRate(dirB, 0.90, 1000);

        int nAt50 = PowerAnalysis.sampleSize(dirA, baseline(), 0.05, 0.80);
        int nAt90 = PowerAnalysis.sampleSize(dirB, baseline(), 0.05, 0.80);

        assertThat(nAt50).isGreaterThan(nAt90);
    }

    @Test
    @DisplayName("required n shrinks as MDE grows (easier-to-detect effect needs fewer samples)")
    void nShrinksAsMdeGrows(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);

        int small = PowerAnalysis.sampleSize(dir, baseline(), 0.01, 0.80);
        int medium = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);
        int large = PowerAnalysis.sampleSize(dir, baseline(), 0.10, 0.80);
        assertThat(small).isGreaterThan(medium).isGreaterThan(large);
    }

    @Test
    @DisplayName("required n grows as power grows (higher confidence in detection needs more samples)")
    void nGrowsAsPowerGrows(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);

        int low = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.50);
        int med = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);
        int high = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.95);
        assertThat(low).isLessThan(med).isLessThan(high);
    }

    @Test
    @DisplayName("throws IllegalStateException when no baseline file matches")
    void noBaselineThrows(@TempDir Path emptyDir) {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(emptyDir, baseline(), 0.05, 0.80))
                .withMessageContaining(USE_CASE_ID)
                .withMessageContaining("run the baseline measure");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when supplier yields a non-MEASURE Experiment")
    void rejectsNonMeasure(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        Supplier<Experiment> exploreSupplier = () -> {
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .useCaseFactory(f -> ECHO)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.exploring(sampling).grid(FACTORS).build();
        };

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, exploreSupplier, 0.05, 0.80))
                .withMessageContaining("MEASURE")
                .withMessageContaining("EXPLORE");
    }

    @Test
    @DisplayName("rejects null baselineDir")
    void rejectsNullBaselineDir() {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(null, baseline(), 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects null baseline supplier")
    void rejectsNullSupplier(@TempDir Path dir) {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects a baseline supplier that returns null")
    void rejectsSupplierReturningNull(@TempDir Path dir) {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, () -> null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects mde out of (0, 1)")
    void rejectsMdeOutOfRange(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 1.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), -0.1, 0.80));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), Double.NaN, 0.80));
    }

    @Test
    @DisplayName("rejects power out of (0, 1)")
    void rejectsPowerOutOfRange(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, 1.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, Double.NaN));
    }

    @Test
    @DisplayName("rejects an MDE incompatible with the resolved baseline rate")
    void rejectsIncompatibleMde(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.50, 1000);
        // rate=0.5 + mde=0.5 → upper bound 1.0, outside (0, 1).
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.5, 0.80))
                .withMessageContaining("incompatible");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.6, 0.80));
    }

    @Test
    @DisplayName("invokes the baseline supplier exactly once")
    void invokesSupplierOnce(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        int[] callCount = {0};
        Supplier<Experiment> counting = () -> {
            callCount[0]++;
            return baseline().get();
        };
        PowerAnalysis.sampleSize(dir, counting, 0.05, 0.80);
        assertThat(callCount[0]).isEqualTo(1);
    }
}
