package org.javai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.punit.api.MatchResult;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ValueMatcher;
import org.javai.punit.api.spec.TerminationReason;
import org.javai.punit.api.PostconditionBuilder;
import org.javai.punit.api.criterion.CriteriaBuilder;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.spec.EngineResult;
import org.javai.punit.api.spec.ExperimentResult;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.FactorsStepper;
import org.javai.punit.api.spec.NextFactor;
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
    private static class LengthServiceContract implements ServiceContract<LlmFactors, String, Integer> {
        @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }

    @Test
    @DisplayName("Experiment runs end-to-end and reports an artefact outcome")
    void measureSpecRunsEndToEnd() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> new LengthServiceContract())
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
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(30)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
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
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(30)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
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
                .serviceContractFactory(f -> new AlwaysReturnsFailServiceContract())
                .inputs(1, 2, 3)
                .samples(15)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
                // Opt this test out of statistical early termination —
                // its assertion exercises the verdict for an all-fail
                // run with the full declared sample count, not the
                // failure-inevitable short-circuit.
                .disableEarlyTermination()
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
                .serviceContractFactory(f -> new AlwaysPassesEmpiricalServiceContract())
                .inputs(1, 2, 3)
                .samples(20)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.3))
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
                .serviceContractFactory(f -> new DefectiveServiceContract())
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
        // Service contract: mirror the input, but uppercase it (deliberately wrong for half).
        ServiceContract<LlmFactors, String, String> upperCase = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.toUpperCase());
            }
        };

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> upperCase)
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
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<String> b) { /* none */ }
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
                .serviceContractFactory(f -> returnSameCase)
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
    @DisplayName("MeasureBuilder.expectedOutputs: length mismatch throws IllegalArgumentException at the call site")
    void rejectsMismatchedExpectedOutputsLength() {
        Sampling<LlmFactors, String, String> sampling = identityStringSampling("a", "b");
        assertThatThrownBy(() -> Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A")) // only one; inputs has two
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("ProbabilisticTest: custom matcher drives the verdict's pass-rate criterion")
    void testPathInstanceConformanceUsesCustomMatcher() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLA);
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(10)
                .build();

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("hello", "world")
                .matcher(caseInsensitive)
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 10);
        assertThat(detail).containsEntry("failures", 0);
    }

    @Test
    @DisplayName("ProbabilisticTest: expectedOutputs without explicit matcher defaults to equality")
    void testPathDefaultsToEqualityMatcher() {
        ServiceContract<LlmFactors, String, String> upperCase = new ServiceContract<>() {
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("contract", pb -> { /* none */ }).meeting(0.4, ThresholdOrigin.SLA);
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.toUpperCase());
            }
        };

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> upperCase)
                .inputs("a", "b")
                .samples(4)
                .build();

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A", "Q") // first matches under equality, second doesn't
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        // 4 samples cycle through 2 expectations: 2 match (A==A), 2 mismatch (B!=Q)
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 2);
        assertThat(detail).containsEntry("failures", 2);
    }

    @Test
    @DisplayName("ProbabilisticTest: matcher configured on Sampling.Builder.matching(...) drives the verdict")
    void testPathPropagatesMatcherFromSampling() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLA);
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(10)
                .matching(List.of("hello", "world"), caseInsensitive)
                .build();

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 10);
        assertThat(detail).containsEntry("failures", 0);
    }

    @Test
    @DisplayName("Experiment: matcher configured on Sampling.Builder.matching(...) drives the measure run")
    void measurePathPropagatesMatcherFromSampling() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(2)
                .matching(List.of("hello", "world"), caseInsensitive)
                .build();

        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0)).build();
        EngineResult outcome = new Engine().run(spec);
        assertThat(((ExperimentResult) outcome).message())
                .contains("passRate=1.000");
    }

    @Test
    @DisplayName("ProbabilisticTest: spec-builder matcher overrides the matcher carried on Sampling")
    void testPathSpecBuilderMatcherOverridesSampling() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLA);
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        ValueMatcher<String> alwaysFail = (exp, act) ->
                MatchResult.fail("alwaysFail", exp, act, "fixture matcher must not run");
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(4)
                .matching(List.of("hello", "world"), alwaysFail)
                .build();

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .matcher(caseInsensitive)  // override
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 4);
        assertThat(detail).containsEntry("failures", 0);
    }

    @Test
    @DisplayName("Experiment: spec-builder matcher overrides the matcher carried on Sampling")
    void measurePathSpecBuilderMatcherOverridesSampling() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        ValueMatcher<String> alwaysFail = (exp, act) ->
                MatchResult.fail("alwaysFail", exp, act, "fixture matcher must not run");
        ValueMatcher<String> caseInsensitive =
                (exp, act) -> exp.equalsIgnoreCase(act)
                        ? MatchResult.pass("equalsIgnoreCase", exp, act)
                        : MatchResult.fail("equalsIgnoreCase", exp, act,
                                "case-insensitive comparison failed");

        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("HELLO", "WORLD")
                .samples(2)
                .matching(List.of("hello", "world"), alwaysFail)
                .build();

        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0))
                .matcher(caseInsensitive)  // override
                .build();
        EngineResult outcome = new Engine().run(spec);
        assertThat(((ExperimentResult) outcome).message())
                .contains("passRate=1.000");
    }

    @Test
    @DisplayName("ProbabilisticTest: spec-builder expectedOutputs overrides the expected list carried on Sampling")
    void testPathSpecBuilderExpectedOverridesSampling() {
        ServiceContract<LlmFactors, String, String> returnSameCase = new ServiceContract<>() {
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLA);
            }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };

        // Sampling carries deliberately-wrong expected values; spec builder
        // restates them correctly. The spec list must win.
        Sampling<LlmFactors, String, String> sampling = Sampling
                .<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> returnSameCase)
                .inputs("a", "b")
                .samples(4)
                .matching(List.of("X", "Y"))   // wrong; equality matcher will fail every sample
                .build();

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("a", "b")     // override with correct values
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("successes", 4);
        assertThat(detail).containsEntry("failures", 0);
    }

    @Test
    @DisplayName("ProbabilisticTest.Builder.expectedOutputs: length mismatch throws IllegalArgumentException at the call site")
    void testPathBoundExpectedOutputsRejectsLengthMismatch() {
        Sampling<LlmFactors, String, String> sampling = identityStringSampling("a", "b");
        assertThatThrownBy(() -> ProbabilisticTest.testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("ProbabilisticTest.Builder.expectedOutputs(varargs): length mismatch throws IllegalArgumentException at the call site")
    void testPathBoundExpectedOutputsVarargsRejectsLengthMismatch() {
        Sampling<LlmFactors, String, String> sampling = identityStringSampling("a", "b");
        assertThatThrownBy(() -> ProbabilisticTest.testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs(new String[] {"A", "B", "C"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (3) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("InlineMeasureBuilder: expectedOutputs after inputs with mismatched length throws IllegalArgumentException at the call site")
    void inlineMeasureRejectsMismatchedExpectedOutputsAfterInputs() {
        assertThatThrownBy(() -> Experiment.measuring(
                        (Function<LlmFactors, ServiceContract<LlmFactors, String, String>>) f -> identityStringServiceContract(),
                        new LlmFactors("gpt-4o", 0.0))
                .inputs("a", "b")
                .expectedOutputs("A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("InlineMeasureBuilder: expectedOutputs before inputs is accepted; mismatched inputs(...) afterwards throws")
    void inlineMeasureDefersThenSymmetricallyRejects() {
        var builder = Experiment.measuring(
                        (Function<LlmFactors, ServiceContract<LlmFactors, String, String>>) f -> identityStringServiceContract(),
                        new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A");   // no throw — inputs not yet set
        assertThatThrownBy(() -> builder.inputs("a", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("InlineMeasureBuilder: expectedOutputs before inputs with matching length builds successfully")
    void inlineMeasureDeferredCheckPassesOnMatchingInputs() {
        Experiment spec = Experiment.measuring(
                        (Function<LlmFactors, ServiceContract<LlmFactors, String, String>>) f -> identityStringServiceContract(),
                        new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A", "B")     // set before inputs
                .inputs("a", "b")               // matching length — symmetric check passes
                .samples(2)
                .build();
        // Run to completion — proves the configured matcher pipeline survives the inline assembly.
        new Engine().run(spec);
        assertThat(spec.lastSummary()).isPresent();
    }

    @Test
    @DisplayName("InlineProbabilisticTest builder: expectedOutputs after inputs with mismatched length throws at the call site")
    void inlineTestRejectsMismatchedExpectedOutputsAfterInputs() {
        assertThatThrownBy(() -> ProbabilisticTest.testing(
                        (Function<LlmFactors, ServiceContract<LlmFactors, String, String>>) f -> identityStringServiceContract(),
                        new LlmFactors("gpt-4o", 0.0))
                .inputs("a", "b")
                .expectedOutputs("A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("InlineProbabilisticTest builder: expectedOutputs before inputs is accepted; mismatched inputs(...) afterwards throws")
    void inlineTestDefersThenSymmetricallyRejects() {
        var builder = ProbabilisticTest.testing(
                        (Function<LlmFactors, ServiceContract<LlmFactors, String, String>>) f -> identityStringServiceContract(),
                        new LlmFactors("gpt-4o", 0.0))
                .expectedOutputs("A");   // no throw — inputs not yet set
        assertThatThrownBy(() -> builder.inputs("a", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    @Test
    @DisplayName("MeasureBuilder.build(): defence-in-depth IllegalStateException still fires when expected/inputs disagree at build time")
    void measureBuildTimeGuardStillFiresAsDefenceInDepth() throws Exception {
        // The call-time check on expectedOutputs(...) means the build-time
        // guard at MeasureBuilder.build() is unreachable through the public
        // API. Reflection lets us bypass the call-time check to confirm the
        // build-time guard remains in place — defence-in-depth coverage.
        Sampling<LlmFactors, String, String> sampling = identityStringSampling("a", "b");
        var builder = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.0));
        var expectedField = Experiment.MeasureBuilder.class.getDeclaredField("expected");
        expectedField.setAccessible(true);
        expectedField.set(builder, List.of("A"));   // bypass call-time check

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expectedOutputs (1) and sampling.inputs() (2) must be the same length");
    }

    private Sampling<LlmFactors, String, String> identityStringSampling(String... inputs) {
        return Sampling.<LlmFactors, String, String>builder()
                .serviceContractFactory(f -> identityStringServiceContract())
                .inputs(inputs)
                .samples(inputs.length)
                .build();
    }

    private static ServiceContract<LlmFactors, String, String> identityStringServiceContract() {
        return new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
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
                .serviceContractFactory(f -> new LengthServiceContract())
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
        // LengthServiceContract returns input.length()
        assertThat(summary.trials().get(2).outcome().result().getOrThrow()).isEqualTo(3);
    }

    @Test
    @DisplayName("Experiment.exploring(shape).grid(...) runs each factors instance once with the shape's sample count")
    void exploreSpecRunsEachFactorBundle() {
        var observedByModel = new java.util.LinkedHashMap<String, Integer>();
        ServiceContract<LlmFactors, String, Integer> counting = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(0);
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> {
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
    @DisplayName("Multi-cell explore: every cell starts its input cursor at zero (no cross-cell bleed)")
    void multiCellExploreStartsEachCellAtInputZero() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(0);
            }
        };
        // Five inputs, three samples per cell — fewer samples than
        // inputs so the legacy cumulative cursor would skip the first
        // two inputs of the second cell.
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("i0", "i1", "i2", "i3", "i4")
                .samples(3)
                .build();
        Experiment spec = Experiment.exploring(shape)
                .grid(
                        new LlmFactors("gpt-4o", 0.0),
                        new LlmFactors("claude-3-sonnet", 0.0))
                .build();

        new Engine().run(spec);

        var perCellTrials = spec.perConfigSummaries();
        assertThat(perCellTrials).hasSize(2);
        for (var perCell : perCellTrials) {
            // Every cell must start at input 0 and walk forward.
            // Pre-fix: cell 1 started at input 3 because cell 0
            // consumed 3 samples, so the factor effect was confounded
            // with input difficulty.
            assertThat(perCell.summary().trials())
                    .extracting(t -> t.inputIndex())
                    .containsExactly(0, 1, 2);
        }
    }

    @Test
    @DisplayName("Experiment.optimizing(shape) runs the stepper/scorer loop up to maxIterations")
    void optimizeSpecRunsIterationLoop() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        // Stepper that walks temperature up by 0.1 each iteration, capping at 1.0.
        FactorsStepper<LlmFactors> stepper = (current, history) ->
                current.temperature() >= 0.95
                        ? NextFactor.stop()
                        : NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.1));
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
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        FactorsStepper<LlmFactors> alwaysAdvance = (current, history) ->
                NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.01));
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
    @DisplayName("NextFactor.stop() ends the optimize loop after the iteration that returned it — no final-iteration sample")
    void nextFactorStopEndsRunCleanly() {
        ServiceContract<LlmFactors, String, Integer> echo = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
        };
        Sampling<LlmFactors, String, Integer> shape = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> echo)
                .inputs("a")
                .samples(1)
                .build();
        // Stop after exactly 3 iterations so we can pin the count precisely.
        FactorsStepper<LlmFactors> stopAfterThree = (current, history) ->
                history.size() >= 3
                        ? NextFactor.stop()
                        : NextFactor.next(new LlmFactors(current.model(), current.temperature() + 0.1));

        Experiment spec = Experiment.optimizing(shape)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper(stopAfterThree)
                .maximize(s -> 0.5)
                .maxIterations(20)             // headroom — Stop must take effect first
                .disableEarlyTermination()     // rule out the no-improvement window as the cause
                .build();

        new Engine().run(spec);
        // Initial factors + 2 stepper-produced advances = 3 iterations, then Stop.
        // No 4th iteration is sampled even though maxIterations(20) would otherwise allow it.
        assertThat(spec.history()).hasSize(3);
    }

    @Test
    @DisplayName("ProbabilisticTest threads its declared intent through to the result")
    void intentThreadsThroughToResult() {
        Sampling<LlmFactors, Integer, Boolean> sampling = Sampling
                .<LlmFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(10)
                .build();
        ProbabilisticTest verification = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .build();
        ProbabilisticTest smoke = ProbabilisticTest
                .testing(sampling, new LlmFactors("gpt-4o", 0.0))
                .intent(TestIntent.SMOKE)
                .build();

        var verResult = (ProbabilisticTestResult) new Engine().run(verification);
        var smkResult = (ProbabilisticTestResult) new Engine().run(smoke);

        assertThat(verResult.intent()).isEqualTo(TestIntent.VERIFICATION);
        assertThat(smkResult.intent()).isEqualTo(TestIntent.SMOKE);
    }

    private List<String> runAndRecord() {
        List<String> observed = new ArrayList<>();
        ServiceContract<LlmFactors, String, Integer> recording = new ServiceContract<>() {
            @Override public void postconditions(PostconditionBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                observed.add(input);
                return Outcome.ok(0);
            }
        };
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .serviceContractFactory(f -> recording)
                .inputs("x", "y", "z")
                .samples(7)
                .build();
        Experiment spec = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();
        new Engine().run(spec);
        return observed;
    }

    // ── Test doubles ────────────────────────────────────────────────

    private static class AlwaysPassesServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public void criteria(CriteriaBuilder<Boolean> b) {
            b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLA);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Empirical-posture sibling of {@link AlwaysPassesServiceContract} for the empirical-path test. */
    private static class AlwaysPassesEmpiricalServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public void criteria(CriteriaBuilder<Boolean> b) {
            b.addCriterion("contract", pb -> { /* none */ }).empirical();
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(Boolean.TRUE);
        }
    }

    /** Returns business-level Fail outcomes — the "contract didn't hold" signal. */
    private static class AlwaysReturnsFailServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public void criteria(CriteriaBuilder<Boolean> b) {
            b.addCriterion("contract", pb -> { /* none */ }).meeting(0.95, ThresholdOrigin.SLO);
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.fail("contract_violation", "scripted failure for input " + input);
        }
    }

    /** Throws — a defect, not a business-level failure. The engine aborts. */
    private static class DefectiveServiceContract implements ServiceContract<LlmFactors, Integer, Boolean> {
        @Override public void postconditions(PostconditionBuilder<Boolean> b) { /* none */ }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            throw new IllegalStateException("simulated defect — this should abort the run");
        }
    }
}
