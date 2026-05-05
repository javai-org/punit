package org.javai.punit.engine.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.engine.Engine;
import org.javai.punit.runtime.OptimizeEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Pure / in-memory tests for the EX06 optimize output writer.
 *
 * <p>The writer is exercised through {@link OptimizeEmitter}'s
 * in-memory sink overload — neither test touches disk.
 */
@DisplayName("OptimizeOutputWriter — EX06 schema, end-to-end via in-memory sink")
class OptimizeOutputWriterTest {

    record LlmFactors(String model, double temperature) {}

    private static class IdUseCase implements UseCase<LlmFactors, String, Integer> {
        private final String id;
        IdUseCase(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }

    @Test
    @DisplayName("OptimizeEmitter writes one YAML carrying iterations + convergence to the sink")
    void emitterCapturesOneFilePerRun() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new IdUseCase("optimize-test"))
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment experiment = Experiment.optimizing(sampling)
                .initialFactors(new LlmFactors("gpt-4o", 0.0))
                .stepper((cur, hist) -> hist.size() < 2
                        ? NextFactor.next(new LlmFactors("gpt-4o", cur.temperature() + 0.3))
                        : NextFactor.stop())
                .maximize(s -> 1.0 * s.successes() / Math.max(1, s.total()))
                .maxIterations(5)
                .experimentId("opt-run-1")
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        OptimizeEmitter.emit(experiment, capture);

        assertThat(sink).hasSize(1);
        assertThat(sink).containsKey("optimize-test/opt-run-1.yaml");

        String yaml = sink.values().iterator().next();
        Map<String, Object> parsed = new Yaml().load(yaml);
        assertThat(parsed).containsEntry("schemaVersion", "punit-spec-1");
        assertThat(parsed).containsEntry("useCaseId", "optimize-test");
        assertThat(parsed).containsEntry("experimentId", "opt-run-1");
        assertThat(parsed).containsEntry("objective", "MAXIMIZE");
        assertThat(parsed).containsKeys("iterations", "convergence", "generatedAt");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations = (List<Map<String, Object>>) parsed.get("iterations");
        assertThat(iterations).isNotEmpty();
        Map<String, Object> first = iterations.get(0);
        assertThat(first).containsKeys("iteration", "factors", "score",
                "successes", "failures", "samplesExecuted");

        @SuppressWarnings("unchecked")
        Map<String, Object> convergence = (Map<String, Object>) parsed.get("convergence");
        assertThat(convergence).containsKeys("totalIterations", "bestIteration",
                "bestScore", "bestFactors", "terminationReason");
    }

    @Test
    @DisplayName("OptimizeEmitter rejects non-OPTIMIZE experiments")
    void emitterRejectsWrongKind() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new IdUseCase("x"))
                .inputs("a")
                .samples(1)
                .build();
        Experiment measure = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        try {
            OptimizeEmitter.emit(measure, new HashMap<String, String>()::put);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("OPTIMIZE");
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for non-OPTIMIZE experiment");
    }
}
