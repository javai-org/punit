package org.javai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.engine.Engine;
import org.javai.punit.engine.criteria.PassRate;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.RunMetadata;
import org.javai.punit.verdict.VerdictAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * End-to-end integration test for the LT01 directive: every artefact
 * type — EX04 (MEASURE baseline), EX05 (EXPLORE per-row), EX06
 * (OPTIMIZE per-iteration), and RP01 (probabilistic-test verdict
 * descriptive latency dimension) — must emit a {@code latency:}
 * block carrying the population-indicator triple, computed from
 * passing samples only.
 *
 * <p>Uses a deliberately mixed pass/fail population (input length
 * triggers the contract's failure clause for some inputs, success
 * for others) so the test can verify {@code contributingSamples <
 * totalSamples} — i.e. failing samples are excluded from the
 * percentile computation.
 *
 * <p>All assertions run against in-memory sinks (no disk).
 */
@DisplayName("LT01 — passing-only latency block surfaces in EX04 / EX05 / EX06 / RP01")
class LatencyEverywhereIntegrationTest {

    record F(String label) {}

    /** Use case that succeeds when input length is even, fails otherwise. */
    private static class EvenLengthUseCase implements UseCase<F, String, Integer> {
        @Override public String id() { return "lt01-test"; }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public void postconditions(ContractBuilder<Integer> b) {
            b.ensure("length is even", n ->
                    n % 2 == 0 ? Outcome.ok() : Outcome.fail("odd-length", "length=" + n));
        }
    }

    /** Mixed inputs: 4 even-length (pass), 2 odd-length (fail). */
    private static final List<String> MIXED_INPUTS = List.of(
            "ab", "cdef", "ghij", "klmn", "x", "yz1");

    @Test
    @DisplayName("EX05 EXPLORE row carries latency block with passing-only indicator")
    void exploreRowCarriesLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .useCaseFactory(f -> new EvenLengthUseCase())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(new F("only")))
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        ExploreEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        Map<String, Object> latency = (Map<String, Object>) parsed.get("latency");
        assertThat(latency)
                .as("EX05 row must carry latency block when at least one sample passed")
                .isNotNull();
        assertThat(latency).containsEntry("basis", "passing-samples");
        assertThat(latency).containsEntry("totalSamples", 6);
        // 4 of 6 inputs are even-length → expect ~4 contributing samples
        // (engine cycles inputs; for 6 samples over 6 inputs each runs once).
        assertThat((Integer) latency.get("contributingSamples"))
                .as("contributingSamples must be ≤ totalSamples and > 0")
                .isPositive()
                .isLessThanOrEqualTo(6);
        assertThat(latency).containsKeys("p50Ms", "p90Ms", "p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("EX06 OPTIMIZE iterations each carry a latency block")
    void optimizeIterationsCarryLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .useCaseFactory(f -> new EvenLengthUseCase())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.optimizing(sampling)
                .initialFactors(new F("init"))
                .stepper((cur, hist) -> hist.size() < 1
                        ? NextFactor.next(new F("step"))
                        : NextFactor.stop())
                .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                .maxIterations(3)
                .experimentId("lt01-opt")
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        OptimizeEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations =
                (List<Map<String, Object>>) parsed.get("iterations");
        assertThat(iterations).isNotEmpty();
        for (Map<String, Object> iter : iterations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> latency = (Map<String, Object>) iter.get("latency");
            assertThat(latency)
                    .as("each iteration with ≥1 passing sample must carry a latency block")
                    .isNotNull();
            assertThat(latency).containsEntry("basis", "passing-samples");
            assertThat(latency).containsKey("contributingSamples");
            assertThat(latency).containsKey("totalSamples");
        }
    }

    @Test
    @DisplayName("EX04 MEASURE baseline carries latency block")
    void measureBaselineCarriesLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .useCaseFactory(f -> new EvenLengthUseCase())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        Experiment experiment = Experiment.measuring(sampling, new F("only")).build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(experiment, sink::put);
        assertThat(sink).hasSize(1);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        @SuppressWarnings("unchecked")
        Map<String, Object> latency = (Map<String, Object>) parsed.get("latency");
        assertThat(latency)
                .as("EX04 baseline must carry latency block when at least one sample passed")
                .isNotNull();
        assertThat(latency).containsEntry("basis", "passing-samples");
        assertThat(latency).containsKey("contributingSamples");
        assertThat(latency).containsKey("totalSamples");
        assertThat(latency).containsKeys("p50Ms", "p90Ms", "p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("RP01 verdict carries descriptive latency dimension when ≥1 passing sample")
    void verdictCarriesDescriptiveLatency() {
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .useCaseFactory(f -> new EvenLengthUseCase())
                .inputs(MIXED_INPUTS)
                .samples(6)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new F("only"))
                .criterion(PassRate.meeting(0.5, ThresholdOrigin.SLA))
                .build();
        ProbabilisticTestResult result = (ProbabilisticTestResult) new Engine().run(spec);
        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                result, RunMetadata.of("LatencyEverywhereIntegrationTest", "verdictTest"));

        assertThat(verdict.latency())
                .as("verdict must carry latency dimension when ≥1 sample passed")
                .isPresent();
        ProbabilisticTestVerdict.LatencyDimension lat = verdict.latency().get();
        assertThat(lat.basis())
                .as("latency dimension must declare passing-samples basis")
                .isEqualTo("passing-samples");
        assertThat(lat.successfulSamples())
                .as("contributing samples must equal verdict.successes for descriptive emission")
                .isEqualTo(verdict.functional().get().successes());
        assertThat(lat.totalSamples()).isEqualTo(6);
        assertThat(lat.assertions())
                .as("descriptive verdict must not carry assertions when LT04 is not active")
                .isEmpty();
    }

    @Test
    @DisplayName("Zero passing samples → no latency block emitted")
    void zeroPassingProducesNoBlock() {
        // Use case that always fails the postcondition.
        UseCase<F, String, Integer> alwaysFail = new UseCase<>() {
            @Override public String id() { return "always-fail"; }
            @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input.length());
            }
            @Override public void postconditions(ContractBuilder<Integer> b) {
                b.ensure("never satisfies", n -> Outcome.fail("nope", "always fails"));
            }
        };
        Sampling<F, String, Integer> sampling = Sampling
                .<F, String, Integer>builder()
                .useCaseFactory(f -> alwaysFail)
                .inputs("a", "b")
                .samples(2)
                .build();
        Experiment experiment = Experiment.measuring(sampling, new F("only")).build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BaselineEmitter.emit(experiment, sink::put);
        Map<String, Object> parsed = new Yaml().load(sink.values().iterator().next());

        assertThat(parsed)
                .as("zero passing samples → latency block omitted entirely")
                .doesNotContainKey("latency");
    }
}
