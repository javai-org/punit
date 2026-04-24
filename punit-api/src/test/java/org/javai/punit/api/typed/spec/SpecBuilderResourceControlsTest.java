package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.function.Function;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Builder-level Stage-3 field validation across all spec flavours.
 *
 * <p>Every per-behaviour spec field added to a builder has a negative
 * test here that asserts build-time rejection of an invalid value
 * (RC01 zero duration, RC02 negative tokens, RC14 negative cap, etc).
 * Defaults are also asserted so accidental regressions surface.
 */
@DisplayName("Stage-3 spec builder resource-control knobs")
class SpecBuilderResourceControlsTest {

    record Factors(String m) {}

    private static Function<Factors, UseCase<Factors, String, Integer>> noopFactory() {
        return f -> new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(String input) {
                return UseCaseOutcome.ok(input.length());
            }
        };
    }

    // ── Defaults ─────────────────────────────────────────────────────

    @Test
    @DisplayName("defaults: MeasureSpec yields empty budgets, FAIL, ABORT_TEST, 10, disabled latency")
    void measureSpecDefaults() {
        MeasureSpec<Factors, String, Integer> spec = baseMeasureBuilder().build();
        assertThat(spec.timeBudget()).isEmpty();
        assertThat(spec.tokenBudget()).isEmpty();
        assertThat(spec.tokenCharge()).isZero();
        assertThat(spec.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.FAIL);
        assertThat(spec.exceptionPolicy()).isEqualTo(ExceptionPolicy.ABORT_TEST);
        assertThat(spec.maxExampleFailures()).isEqualTo(10);
        assertThat(spec.latency().isDisabled()).isTrue();
    }

    @Test
    @DisplayName("defaults: ProbabilisticTestSpec (normative) assertOn() is FUNCTIONAL when no latency declared")
    void probSpecAssertOnDefaultFunctional() {
        ProbabilisticTestSpec<Factors, String, Integer> spec = baseProbNormativeBuilder().build();
        assertThat(spec.assertOn()).isEqualTo(Dimension.FUNCTIONAL);
    }

    @Test
    @DisplayName("defaults: ProbabilisticTestSpec (normative) assertOn() becomes BOTH when latency declared")
    void probSpecAssertOnDefaultBothWithLatency() {
        ProbabilisticTestSpec<Factors, String, Integer> spec = baseProbNormativeBuilder()
                .latency(LatencySpec.builder().p95Millis(500L).build())
                .build();
        assertThat(spec.assertOn()).isEqualTo(Dimension.BOTH);
    }

    @Test
    @DisplayName("explicit assertOn() overrides the derived default")
    void assertOnExplicitOverride() {
        ProbabilisticTestSpec<Factors, String, Integer> spec = baseProbNormativeBuilder()
                .latency(LatencySpec.builder().p95Millis(500L).build())
                .assertOn(Dimension.LATENCY)
                .build();
        assertThat(spec.assertOn()).isEqualTo(Dimension.LATENCY);
    }

    // ── Positive round-trip ──────────────────────────────────────────

    @Test
    @DisplayName("MeasureSpec builder round-trips every Stage-3 knob")
    void measureSpecRoundTripsEveryKnob() {
        MeasureSpec<Factors, String, Integer> spec = baseMeasureBuilder()
                .timeBudget(Duration.ofSeconds(5))
                .tokenBudget(1000)
                .tokenCharge(50)
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .maxExampleFailures(25)
                .latency(LatencySpec.builder().p95Millis(300L).build())
                .build();

        assertThat(spec.timeBudget()).hasValue(Duration.ofSeconds(5));
        assertThat(spec.tokenBudget()).hasValue(1000L);
        assertThat(spec.tokenCharge()).isEqualTo(50L);
        assertThat(spec.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.PASS_INCOMPLETE);
        assertThat(spec.exceptionPolicy()).isEqualTo(ExceptionPolicy.FAIL_SAMPLE);
        assertThat(spec.maxExampleFailures()).isEqualTo(25);
        assertThat(spec.latency().p95Millis()).hasValue(300L);
    }

    // ── Negative: each new field ────────────────────────────────────

    @Test
    @DisplayName("timeBudget rejects zero and negative durations")
    void rejectsZeroTimeBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().timeBudget(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().timeBudget(Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("tokenBudget rejects zero and negative values")
    void rejectsNonPositiveTokenBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().tokenBudget(0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().tokenBudget(-100));
    }

    @Test
    @DisplayName("tokenCharge rejects negative values (zero is allowed — default)")
    void rejectsNegativeTokenCharge() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().tokenCharge(-1));
    }

    @Test
    @DisplayName("maxExampleFailures rejects negative values (zero is allowed — count only)")
    void rejectsNegativeMaxExampleFailures() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseMeasureBuilder().maxExampleFailures(-1));
    }

    // ── Same negative tests across the other builders ───────────────

    @Test
    @DisplayName("ExploreSpec builder validates Stage-3 knobs the same way")
    void exploreSpecValidations() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseExploreBuilder().timeBudget(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseExploreBuilder().tokenBudget(0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseExploreBuilder().tokenCharge(-1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseExploreBuilder().maxExampleFailures(-1));
    }

    @Test
    @DisplayName("OptimizeSpec builder validates Stage-3 knobs the same way")
    void optimizeSpecValidations() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseOptimizeBuilder().timeBudget(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseOptimizeBuilder().tokenBudget(-1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseOptimizeBuilder().tokenCharge(-1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseOptimizeBuilder().maxExampleFailures(-1));
    }

    @Test
    @DisplayName("ProbabilisticTestSpec normative builder validates Stage-3 knobs")
    void probNormativeSpecValidations() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbNormativeBuilder().timeBudget(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbNormativeBuilder().tokenBudget(-5));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbNormativeBuilder().tokenCharge(-1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbNormativeBuilder().maxExampleFailures(-1));
    }

    @Test
    @DisplayName("ProbabilisticTestSpec empirical builder validates Stage-3 knobs")
    void probEmpiricalSpecValidations() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbEmpiricalBuilder().timeBudget(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbEmpiricalBuilder().tokenBudget(-5));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbEmpiricalBuilder().tokenCharge(-1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseProbEmpiricalBuilder().maxExampleFailures(-1));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static MeasureSpec.Builder<Factors, String, Integer> baseMeasureBuilder() {
        return MeasureSpec.<Factors, String, Integer>builder()
                .useCaseFactory(noopFactory())
                .factors(new Factors("m"))
                .inputs("a")
                .samples(1);
    }

    private static ExploreSpec.Builder<Factors, String, Integer> baseExploreBuilder() {
        return ExploreSpec.<Factors, String, Integer>builder()
                .useCaseFactory(noopFactory())
                .factors(new Factors("m"))
                .inputs("a")
                .samplesPerConfig(1);
    }

    private static OptimizeSpec.Builder<Factors, String, Integer> baseOptimizeBuilder() {
        FactorMutator<Factors> mutator = (current, history) -> null;
        Scorer scorer = summary -> summary.passRate();
        return OptimizeSpec.<Factors, String, Integer>builder()
                .useCaseFactory(noopFactory())
                .initialFactors(new Factors("m"))
                .inputs("a")
                .mutator(mutator)
                .scorer(scorer)
                .samplesPerIteration(1)
                .maxIterations(1);
    }

    private static ProbabilisticTestSpec.NormativeBuilder<Factors, String, Integer> baseProbNormativeBuilder() {
        return ProbabilisticTestSpec.<Factors, String, Integer>normative()
                .useCaseFactory(noopFactory())
                .factors(new Factors("m"))
                .inputs("a")
                .samples(10)
                .threshold(0.9, ThresholdOrigin.SLA);
    }

    private static ProbabilisticTestSpec.EmpiricalBuilder<Factors, String, Integer> baseProbEmpiricalBuilder() {
        return ProbabilisticTestSpec.<Factors, String, Integer>basedOn(
                        () -> MeasureSpec.<Factors, String, Integer>builder()
                                .useCaseFactory(noopFactory())
                                .factors(new Factors("m"))
                                .inputs("a")
                                .samples(10)
                                .build())
                .useCaseFactory(noopFactory())
                .factors(new Factors("m"))
                .inputs("a")
                .samples(10);
    }
}
