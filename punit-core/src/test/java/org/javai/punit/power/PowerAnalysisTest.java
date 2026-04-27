package org.javai.punit.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Supplier;

import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.Experiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PowerAnalysis.sampleSize")
class PowerAnalysisTest {

    record Factors(String label) {}

    private static final UseCase<Factors, String, String> ECHO = new UseCase<>() {
        @Override public UseCaseOutcome<String> apply(String input) { return UseCaseOutcome.ok(input); }
    };

    private static Supplier<Experiment> baseline() {
        return () -> {
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .useCaseFactory(f -> ECHO)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, new Factors("m")).build();
        };
    }

    @Test
    @DisplayName("returns a positive integer for valid inputs")
    void returnsPositiveInteger() {
        int n = PowerAnalysis.sampleSize(baseline(), 0.02, 0.80);
        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("required n shrinks as MDE grows (easier-to-detect effect needs fewer samples)")
    void nShrinksAsMdeGrows() {
        int small = PowerAnalysis.sampleSize(baseline(), 0.01, 0.80);
        int medium = PowerAnalysis.sampleSize(baseline(), 0.05, 0.80);
        int large = PowerAnalysis.sampleSize(baseline(), 0.10, 0.80);
        assertThat(small).isGreaterThan(medium).isGreaterThan(large);
    }

    @Test
    @DisplayName("required n grows as power grows (higher confidence in detection needs more samples)")
    void nGrowsAsPowerGrows() {
        int low = PowerAnalysis.sampleSize(baseline(), 0.05, 0.50);
        int med = PowerAnalysis.sampleSize(baseline(), 0.05, 0.80);
        int high = PowerAnalysis.sampleSize(baseline(), 0.05, 0.95);
        assertThat(low).isLessThan(med).isLessThan(high);
    }

    @Test
    @DisplayName("rejects null baseline supplier")
    void rejectsNullSupplier() {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects a baseline supplier that returns null")
    void rejectsSupplierReturningNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(() -> null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects mde out of (0, 1)")
    void rejectsMdeOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 1.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), -0.1, 0.80));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), Double.NaN, 0.80));
    }

    @Test
    @DisplayName("rejects power out of (0, 1)")
    void rejectsPowerOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.05, 0.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.05, 1.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.05, Double.NaN));
    }

    @Test
    @DisplayName("rejects an MDE incompatible with the resolved baseline rate")
    void rejectsIncompatibleMde() {
        // Stage-3.5 placeholder rate is 0.5; mde >= 0.5 pushes the
        // [rate-mde, rate+mde] interval outside (0, 1).
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.5, 0.80))
                .withMessageContaining("incompatible");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(baseline(), 0.6, 0.80));
    }

    @Test
    @DisplayName("invokes the baseline supplier exactly once")
    void invokesSupplierOnce() {
        int[] callCount = {0};
        Supplier<Experiment> counting = () -> {
            callCount[0]++;
            return baseline().get();
        };
        PowerAnalysis.sampleSize(counting, 0.05, 0.80);
        assertThat(callCount[0]).isEqualTo(1);
    }
}
