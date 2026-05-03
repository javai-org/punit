package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.javai.outcome.Outcome;
import org.javai.punit.api.MatchResult;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ValueMatcher;
import org.javai.punit.api.spec.TerminationReason;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.engine.criteria.PassRate;
import org.javai.punit.api.spec.EngineResult;
import org.javai.punit.api.spec.ExperimentResult;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.FactorsStepper;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hand-wired end-to-end test for the engine.
 *
 * <p>No JUnit extensions, no annotation scanning, no reflection
 * into PUnit internals. Constructs specs, runs them through
 * {@link Engine}, and asserts the resulting {@link EngineResult}.
 */
@DisplayName("Engine end-to-end integration")
class EngineIntegrationTest {

    record LlmFactors(String model, double temperature) {}

    /** Always returns the length of the input. Used as a deterministic sample. */
    private static class LengthUseCase implements UseCase<LlmFactors, String, Integer> {
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }

    @Test
    @DisplayName("Experiment runs end-to-end and reports an artefact outcome")
    void measureSpecRunsEndToEnd() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new LengthUseCase())
                .inputs("a", "bb", "ccc")
                .samples(9)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        ExperimentResult art = (ExperimentResult) outcome;
        assertThat(art.destination().toString()).startsWith("specs");
        assertThat(art.message()).contains("samples=9");
    }

    @Test
    @DisplayName("ProbabilisticTest with PassRate.meeting() produces PASS when observed beats threshold")
    void contractualProducesPass() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .inputs(1, 2, 3)
                .samples(30)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .build();

        EngineResult outcome = new Engine().run(spec);

        assertThat(outcome).isInstanceOf(ProbabilisticTestResult.class);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults()).hasSize(1);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("origin", "SLA");
        assertThat(detail).containsEntry("threshold", 0.95);
        assertThat(detail).containsEntry("total", 30);
    }

    @Test
    @DisplayName("ProbabilisticTestResult carries engine-run summary populated from SampleSummary")
    void engineSummaryPopulated() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .inputs(1, 2, 3)
                .samples(30)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);

        var summary = result.engineSummary();
        assertThat(summary.plannedSamples()).isEqualTo(30);
        assertThat(summary.samplesExecuted()).isEqualTo(30);
        assertThat(summary.successes()).isEqualTo(30);
        assertThat(summary.failures()).isZero();
        assertThat(summary.elapsedMs()).isGreaterThanOrEqualTo(0L);
        assertThat(summary.terminationReason())
                .isEqualTo(TerminationReason.COMPLETED);
        // Contractual mode: confidence falls back to default 0.95 since
        // PassRate's contractual path doesn't surface confidence
        // in detail map.
        assertThat(summary.confidence()).isEqualTo(0.95);
        assertThat(summary.baselineFilename()).isEmpty();
    }

    @Test
    @DisplayName("ProbabilisticTest with PassRate.meeting() produces FAIL when observed below threshold")
    void contractualProducesFail() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysReturnsFailUseCase())
                .inputs(1, 2, 3)
                .samples(15)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLO))
                .build();

        EngineResult outcome = new Engine().run(spec);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 0);
        assertThat(detail).containsEntry("failures", 15);
    }

    @Test
    @DisplayName("PassRate.empirical() yields INCONCLUSIVE under the Stage-3.5 stub baseline resolver")
    void empiricalYieldsInconclusiveUnderStubResolver() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .inputs(1, 2, 3)
                .samples(20)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                .criterion(PassRate.<Boolean>empirical())
                .build();

        EngineResult outcome = new Engine().run(spec);
        var result = (ProbabilisticTestResult) outcome;
        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults()).hasSize(1);
        assertThat(result.criterionResults().get(0).result().verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("baseline");
    }

    @Test
    @DisplayName("A thrown exception is treated as a defect and aborts the run")
    void defectAbortsTheRun() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new DefectiveUseCase())
                .inputs(1, 2, 3)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        assertThatThrownBy(() -> new Engine().run(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated defect");
    }

    @Test
    @DisplayName("expectations: matching actuals count as successes; mismatches count as failures")
    void instanceConformanceDrivesVerdict() {
        // Use case: mirror the input, but uppercase it (deliberately wrong for half).
        UseCase<LlmFactors, String, String> upperCase = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.toUpperCase());
            }
        };

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> upperCase)
                .inputs("a", "b")
                .samples(4)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A", "Q") // match, mismatch
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
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };

        // Custom matcher: case-insensitive comparison.
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(2)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("hello", "world")
                .matcher(caseInsensitive)
                .build();

        EngineResult outcome = new Engine().run(spec);
        assertThat(((ExperimentResult) outcome).message())
                .contains("passRate=1.000");
    }

    @Test
    @DisplayName("Experiment rejects expectedOutputs whose length does not match sampling.inputs")
    void rejectsMismatchedExpectedOutputsLength() {
        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .useCaseFactory(f -> new UseCase<LlmFactors, String, String>() {
                    @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
                    @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                        return Outcome.ok(input);
                    }
                })
                .inputs("a", "b")
                .build();
        assertThatThrownBy(() -> Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A") // only one; inputs has two
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expectedOutputs")
                .hasMessageContaining("inputs");
    }

    @Test
    @DisplayName("Round-robin input cycling is deterministic across runs")
    void roundRobinInputCyclingIsDeterministic() {
        List<String> observedA = runAndRecord();
        List<String> observedB = runAndRecord();
        assertThat(observedA).isEqualTo(observedB);
    }

    @Test
    @DisplayName("Engine populates SampleSummary.trials with one entry per sample, in order, with the cycled input")
    void enginePopulatesOrderedTrials() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new LengthUseCase())
                .inputs("a", "bb", "ccc")
                .samples(7)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0)).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.trials()).hasSize(7);
        // Round-robin order: a, bb, ccc, a, bb, ccc, a
        assertThat(summary.trials().get(0).input()).isEqualTo("a");
        assertThat(summary.trials().get(1).input()).isEqualTo("bb");
        assertThat(summary.trials().get(2).input()).isEqualTo("ccc");
        assertThat(summary.trials().get(3).input()).isEqualTo("a");
        assertThat(summary.trials().get(6).input()).isEqualTo("a");
        // LengthUseCase returns input.length()
        assertThat(summary.trials().get(2).outcome().result().getOrThrow()).isEqualTo(3);
    }

    @Test
    @DisplayName("Experiment.exploring(shape).grid(...) runs each factors instance once with the shape's sample count")
    void exploreSpecRunsEachFactorBundle() {
        var observedByModel = new java.util.LinkedHashMap<String, Integer>();
        UseCase<LlmFactors, String, Integer> counting = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(0);
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> {
                    observedByModel.merge(f.model(), 0, (a, b) -> a);
                    return counting;
                })
                .inputs("a", "b")
                .samples(3)
                .build();
        Experiment spec = Experiment.exploring(shape)
                .grid(
                        new LlmFactors("gpt-4o", 0.0),
                        new LlmFactors("gpt-4o", 0.5),
                        new LlmFactors("claude-3-sonnet", 0.0))
                .build();

        EngineResult outcome = new Engine().run(spec);
        assertThat(outcome).isInstanceOf(ExperimentResult.class);
        assertThat(((ExperimentResult) outcome).message()).contains("configurations=3");
        assertThat(observedByModel).containsOnlyKeys("gpt-4o", "claude-3-sonnet");
    }

    @Test
    @DisplayName("Experiment.optimizing(shape) runs the stepper/scorer loop up to maxIterations")
    void optimizeSpecRunsIterationLoop() {
        UseCase<LlmFactors, String, Integer> echo = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        // Stepper that walks temperature up by 0.1 each iteration, capping at 1.0.
        FactorsStepper<LlmFactors> stepper = (current, history) ->
                current.temperature() >= 0.95
                        ? null
                        : new LlmFactors(current.model(), current.temperature() + 0.1);
        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(stepper)
                .maximize(s -> 1.0 / (1.0 + s.failures()))
                .maxIterations(5)
                .noImprovementWindow(10)
                .build();

        new Engine().run(spec);
        assertThat(spec.history()).hasSize(5);
    }

    @Test
    @DisplayName("disableEarlyTermination() runs to maxIterations even when scores are flat")
    void disableEarlyTerminationRunsToMaxIterations() {
        UseCase<LlmFactors, String, Integer> echo = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        FactorsStepper<LlmFactors> alwaysAdvance = (current, history) ->
                new LlmFactors(current.model(), current.temperature() + 0.01);
        // Constant scorer: every iteration ties the previous, so the
        // default no-improvement window would terminate the run early.
        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(alwaysAdvance)
                .maximize(s -> 0.5)
                .maxIterations(8)
                .disableEarlyTermination()
                .build();

        new Engine().run(spec);
        assertThat(spec.history()).hasSize(8);
    }

    @Test
    @DisplayName("ProbabilisticTest threads its declared intent through to the result")
    void intentThreadsThroughToResult() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .inputs(1, 2, 3)
                .samples(10)
                .build();
        ProbabilisticTest verification = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .build();
        ProbabilisticTest smoke = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .criterion(PassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .intent(TestIntent.SMOKE)
                .build();

        var verResult = (ProbabilisticTestResult) new Engine().run(verification);
        var smkResult = (ProbabilisticTestResult) new Engine().run(smoke);

        assertThat(verResult.intent()).isEqualTo(TestIntent.VERIFICATION);
        assertThat(smkResult.intent()).isEqualTo(TestIntent.SMOKE);
    }

    private List<String> runAndRecord() {
        List<String> observed = new ArrayList<>();
        UseCase<LlmFactors, String, Integer> recording = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                observed.add(input);
                return Outcome.ok(0);
            }
        };
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> recording)
                .inputs("x", "y", "z")
                .samples(7)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();
        new Engine().run(spec);
        return observed;
    }

    // ── Test doubles ────────────────────────────────────────────────

    private static class AlwaysPassesUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Returns business-level Fail outcomes — the "contract didn't hold" signal. */
    private static class AlwaysReturnsFailUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.fail("contract_violation", "scripted failure for input " + input);
        }
    }

    /** Throws — a defect, not a business-level failure. The engine aborts. */
    private static class DefectiveUseCase implements UseCase<LlmFactors, Integer, Boolean> {
        @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            throw new IllegalStateException("simulated defect — this should abort the run");
        }
    }
}
