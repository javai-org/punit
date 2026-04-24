package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.EngineResult;
import org.javai.punit.api.typed.spec.ExperimentResult;
import org.javai.punit.api.typed.spec.ProbabilisticTestResult;
import org.javai.punit.api.typed.spec.MeasureSpec;
import org.javai.punit.api.typed.spec.ProbabilisticTestSpec;
import org.javai.punit.api.typed.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hand-wired end-to-end test for the typed-model engine.
 *
 * <p>No JUnit extensions, no annotation scanning, no reflection
 * into PUnit internals. Constructs typed specs, runs them through
 * {@link Engine}, and asserts the resulting {@link EngineResult}.
 *
 * <p>This is the Stage 2 validation signal.
 */
@DisplayName("Engine end-to-end integration")
class EngineIntegrationTest {

    record LlmFactors(String model, double temperature) {}

    /** Always returns the length of the input. Used as a deterministic sample. */
    private static class LengthUseCase implements UseCase<LlmFactors, String, Integer> {
        @Override public UseCaseOutcome<Integer> apply(String input) {
            return UseCaseOutcome.ok(input.length());
        }
    }

    @Test
    @DisplayName("MeasureSpec runs end-to-end and reports an artefact outcome")
    void measureSpecRunsEndToEnd() {
        MeasureSpec<LlmFactors, String, Integer> spec = MeasureSpec.<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new LengthUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs("a", "bb", "ccc")
                .samples(9)
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        ExperimentResult art = (ExperimentResult) outcome;
        assertThat(art.destination().toString()).startsWith("specs");
        assertThat(art.message()).contains("samples=9");
    }

    @Test
    @DisplayName("ProbabilisticTestSpec (normative) produces a PASS when observed rate beats threshold")
    void normativeProducesPass() {
        ProbabilisticTestSpec<LlmFactors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<LlmFactors, Integer, Boolean>normative()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs(1, 2, 3)
                .samples(30)
                .threshold(0.95, ThresholdOrigin.SLA)
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ProbabilisticTestResult.class);
        var verdict = ((ProbabilisticTestResult) outcome);
        assertThat(verdict.verdict()).isEqualTo(Verdict.PASS);
        assertThat(verdict.thresholdOrigin()).isEqualTo(ThresholdOrigin.SLA);
        assertThat(verdict.threshold()).isEqualTo(0.95);
        assertThat(verdict.total()).isEqualTo(30);
    }

    @Test
    @DisplayName("ProbabilisticTestSpec (normative) produces a FAIL when a use case returns business-level Fail outcomes")
    void normativeProducesFail() {
        ProbabilisticTestSpec<LlmFactors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<LlmFactors, Integer, Boolean>normative()
                .useCaseFactory(f -> new AlwaysReturnsFailUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs(1, 2, 3)
                .samples(15)
                .threshold(0.95, ThresholdOrigin.SLO)
                .build();

        EngineResult outcome = new Engine().run(spec);
        var verdict = ((ProbabilisticTestResult) outcome);
        assertThat(verdict.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(verdict.successes()).isZero();
        assertThat(verdict.failures()).isEqualTo(15);
    }

    @Test
    @DisplayName("ProbabilisticTestSpec (empirical) produces a verdict with EMPIRICAL threshold origin")
    void empiricalProducesVerdictWithEmpiricalOrigin() {
        ProbabilisticTestSpec<LlmFactors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<LlmFactors, Integer, Boolean>basedOn(() -> MeasureSpec.<LlmFactors, Integer, Boolean>builder()
                        .useCaseFactory(f -> new AlwaysPassesUseCase())
                        .factors(new LlmFactors("gpt-4o", 0.3))
                        .inputs(1, 2, 3)
                        .samples(10)
                        .build())
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs(1, 2, 3)
                .samples(20)
                .build();

        EngineResult outcome = new Engine().run(spec);
        var verdict = ((ProbabilisticTestResult) outcome);
        assertThat(verdict.thresholdOrigin()).isEqualTo(ThresholdOrigin.EMPIRICAL);
        assertThat(verdict.warnings())
                .anyMatch(w -> w.contains("placeholder"));
    }

    @Test
    @DisplayName("A thrown exception is treated as a defect and aborts the run")
    void defectAbortsTheRun() {
        MeasureSpec<LlmFactors, Integer, Boolean> spec = MeasureSpec
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new DefectiveUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs(1, 2, 3)
                .samples(5)
                .build();

        assertThatThrownBy(() -> new Engine().run(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated defect");
    }

    @Test
    @DisplayName("expectations: matching actuals count as successes; mismatches count as failures")
    void instanceConformanceDrivesVerdict() {
        // Use case: mirror the input, but uppercase it (deliberately wrong for half).
        UseCase<LlmFactors, String, String> upperCase = new UseCase<>() {
            @Override public UseCaseOutcome<String> apply(String input) {
                return UseCaseOutcome.ok(input.toUpperCase());
            }
        };

        MeasureSpec<LlmFactors, String, String> spec = MeasureSpec
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> upperCase)
                .factors(new LlmFactors("gpt-4o", 0.0))
                .expectations(
                        org.javai.punit.api.typed.Expectation.of("a", "A"),   // match
                        org.javai.punit.api.typed.Expectation.of("b", "Q"))   // mismatch
                .samples(4)
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        // 4 samples cycle through 2 expectations => 2 match, 2 mismatch
        assertThat(((ExperimentResult) outcome).message())
                .contains("samples=4")
                .contains("passRate=0.500");
    }

    @Test
    @DisplayName("expectations: custom matcher overrides equality default")
    void instanceConformanceUsesCustomMatcher() {
        UseCase<LlmFactors, String, String> returnSameCase = new UseCase<>() {
            @Override public UseCaseOutcome<String> apply(String input) {
                return UseCaseOutcome.ok(input);
            }
        };

        // Custom matcher: case-insensitive comparison.
        org.javai.punit.api.typed.ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? org.javai.punit.api.typed.MatchResult.pass("equalsIgnoreCase", exp, act)
                        : org.javai.punit.api.typed.MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        MeasureSpec<LlmFactors, String, String> spec = MeasureSpec
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> returnSameCase)
                .factors(new LlmFactors("gpt-4o", 0.0))
                .expectations(
                        org.javai.punit.api.typed.Expectation.of("HELLO", "hello"),
                        org.javai.punit.api.typed.Expectation.of("WORLD", "world"))
                .matcher(caseInsensitive)
                .samples(2)
                .build();

        EngineResult outcome = new Engine().run(spec);
        assertThat(((ExperimentResult) outcome).message())
                .contains("passRate=1.000");
    }

    @Test
    @DisplayName("builder rejects both .inputs() and .expectations() on the same spec")
    void cannotCombineInputsAndExpectations() {
        MeasureSpec.Builder<LlmFactors, String, String> b = MeasureSpec
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> new UseCase<LlmFactors, String, String>() {
                    @Override public UseCaseOutcome<String> apply(String input) {
                        return UseCaseOutcome.ok(input);
                    }
                })
                .factors(new LlmFactors("gpt-4o", 0.0))
                .inputs("a");
        assertThatThrownBy(() -> b.expectations(
                org.javai.punit.api.typed.Expectation.of("a", "A")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".inputs(...)")
                .hasMessageContaining(".expectations(...)");
    }

    @Test
    @DisplayName("Round-robin input cycling is deterministic across runs")
    void roundRobinInputCyclingIsDeterministic() {
        List<String> observedA = runAndRecord();
        List<String> observedB = runAndRecord();
        assertThat(observedA).isEqualTo(observedB);
    }

    private List<String> runAndRecord() {
        List<String> observed = new ArrayList<>();
        UseCase<LlmFactors, String, Integer> recording = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(String input) {
                observed.add(input);
                return UseCaseOutcome.ok(0);
            }
        };
        MeasureSpec<LlmFactors, String, Integer> spec = MeasureSpec
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> recording)
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs("x", "y", "z")
                .samples(7)
                .build();
        new Engine().run(spec);
        return observed;
    }

    // ── Test doubles ────────────────────────────────────────────────

    private static class AlwaysPassesUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.ok(Boolean.TRUE);
        }
    }

    /** Returns business-level Fail outcomes — the "contract didn't hold" signal. */
    private static class AlwaysReturnsFailUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.fail("contract_violation", "scripted failure for input " + input);
        }
    }

    /** Throws — a defect, not a business-level failure. The engine aborts. */
    private static class DefectiveUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            throw new IllegalStateException("simulated defect — this should abort the run");
        }
    }
}
