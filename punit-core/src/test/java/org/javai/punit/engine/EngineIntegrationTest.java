package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.EngineOutcome;
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
 * {@link Engine}, and asserts the resulting {@link EngineOutcome}.
 *
 * <p>This is the Stage 2 validation signal.
 */
@DisplayName("Engine end-to-end integration")
class EngineIntegrationTest {

    record LlmFactors(String model, double temperature) {}

    /** Always returns the length of the input. Used as a deterministic sample. */
    private static class LengthUseCase implements UseCase<LlmFactors, String, Integer> {
        @Override public UseCaseOutcome<Integer> apply(String input) {
            return UseCaseOutcome.of(input.length());
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

        EngineOutcome outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(EngineOutcome.Artefact.class);
        EngineOutcome.Artefact art = (EngineOutcome.Artefact) outcome;
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

        EngineOutcome outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(EngineOutcome.ProbabilisticTestVerdict.class);
        var verdict = ((EngineOutcome.ProbabilisticTestVerdict) outcome).verdictOutcome();
        assertThat(verdict.verdict()).isEqualTo(Verdict.PASS);
        assertThat(verdict.thresholdOrigin()).isEqualTo(ThresholdOrigin.SLA);
        assertThat(verdict.threshold()).isEqualTo(0.95);
        assertThat(verdict.total()).isEqualTo(30);
    }

    @Test
    @DisplayName("ProbabilisticTestSpec (normative) produces a FAIL when observed rate below threshold")
    void normativeProducesFail() {
        ProbabilisticTestSpec<LlmFactors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<LlmFactors, Integer, Boolean>normative()
                .useCaseFactory(f -> new AlwaysFailsUseCase())
                .factors(new LlmFactors("gpt-4o", 0.3))
                .inputs(1, 2, 3)
                .samples(15)
                .threshold(0.95, ThresholdOrigin.SLO)
                .build();

        EngineOutcome outcome = new Engine().run(spec);
        var verdict = ((EngineOutcome.ProbabilisticTestVerdict) outcome).verdictOutcome();
        assertThat(verdict.verdict()).isEqualTo(Verdict.FAIL);
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

        EngineOutcome outcome = new Engine().run(spec);
        var verdict = ((EngineOutcome.ProbabilisticTestVerdict) outcome).verdictOutcome();
        assertThat(verdict.thresholdOrigin()).isEqualTo(ThresholdOrigin.EMPIRICAL);
        assertThat(verdict.warnings())
                .anyMatch(w -> w.contains("Stage 4"));
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
                return UseCaseOutcome.of(0);
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
            return UseCaseOutcome.of(Boolean.TRUE);
        }
    }

    private static class AlwaysFailsUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            throw new RuntimeException("scripted failure");
        }
    }
}
