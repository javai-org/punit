package org.javai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.NoFactors;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.javai.punit.engine.Engine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

@DisplayName("BaselineEmitter — misuse contract and per-sample emission")
class BaselineEmitterTest {

    private static final UseCase<NoFactors, Integer, Boolean> ALWAYS_PASSES = new UseCase<>() {
        @Override public String id() { return "AlwaysPassesUseCase"; }
        @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(true);
        }
    };

    private static Sampling<NoFactors, Integer, Boolean> sampling() {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> ALWAYS_PASSES)
                .inputs(1, 2, 3)
                .samples(10)
                .build();
    }

    @Test
    @DisplayName("rejects an EXPLORE experiment with IllegalArgumentException")
    void rejectsExplore(@TempDir Path dir) {
        Experiment explore = Experiment.exploring(sampling())
                .grid(new NoFactors())
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BaselineEmitter.emit(explore, dir))
                .withMessageContaining("MEASURE")
                .withMessageContaining("EXPLORE");
    }

    @Test
    @DisplayName("rejects an OPTIMIZE experiment with IllegalArgumentException")
    void rejectsOptimize(@TempDir Path dir) {
        Experiment optimize = Experiment.optimizing(sampling())
                .initialFactors(new NoFactors())
                .stepper((current, history) -> history.size() >= 1 ? NextFactor.stop() : NextFactor.next(new NoFactors()))
                .maximize(s -> 0.0)
                .maxIterations(1)
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BaselineEmitter.emit(optimize, dir))
                .withMessageContaining("MEASURE")
                .withMessageContaining("OPTIMIZE");
    }

    @Test
    @DisplayName("rejects a MEASURE experiment that has not yet been consumed by the engine")
    void rejectsUnconsumedMeasure(@TempDir Path dir) {
        Experiment measure = Experiment.measuring(sampling(), new NoFactors()).build();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> BaselineEmitter.emit(measure, dir))
                .withMessageContaining("no recorded summary");
    }

    private static final UseCase<NoFactors, Integer, String> EVENS_PASS = new UseCase<>() {
        @Override public String id() { return "EvensPassUseCase"; }
        @Override public void postconditions(ContractBuilder<String> b) {
            b.ensure("non-blank", s -> s.isBlank()
                    ? Outcome.fail("blank", "value was blank")
                    : Outcome.ok(null));
        }
        @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
            return input % 2 == 0 ? Outcome.ok("even-" + input) : Outcome.fail("odd", "got " + input);
        }
    };

    @Test
    @DisplayName("emits a per-sample resultProjection: block carrying every trial — "
            + "inputIndex, postconditions, executionTimeMs, content-or-failureDetail")
    void emitsResultProjection() {
        Sampling<NoFactors, Integer, String> sampling = Sampling
                .<NoFactors, Integer, String>builder()
                .useCaseFactory(f -> EVENS_PASS)
                .inputs(2, 3) // alternating pass / fail by parity
                .samples(4)   // 4 samples cycle the 2-input list twice
                .build();
        Experiment measure = Experiment.measuring(sampling, new NoFactors()).build();
        new Engine().run(measure);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        BaselineEmitter.emit(measure, capture);

        assertThat(sink).hasSize(1);
        String yaml = sink.values().iterator().next();
        Map<String, Object> root = new Yaml().load(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> projection = (Map<String, Object>) root.get("resultProjection");
        assertThat(projection)
                .as("MEASURE baselines must carry a resultProjection: block — "
                        + "per-sample observations are not lost to bulk")
                .isNotNull();
        // 4 samples → 4 sample[N] entries in iteration order.
        assertThat(projection).containsKeys("sample[0]", "sample[1]", "sample[2]", "sample[3]");

        // Cycling: sample[0] → input 2 (index 0, passes), sample[1] → input 3 (index 1, fails),
        // sample[2] → input 2 (index 0, passes), sample[3] → input 3 (index 1, fails).
        @SuppressWarnings("unchecked")
        Map<String, Object> s0 = (Map<String, Object>) projection.get("sample[0]");
        assertThat(s0).containsKeys("inputIndex", "postconditions", "executionTimeMs", "content");
        assertThat(s0).doesNotContainKey("input");
        assertThat(s0).containsEntry("inputIndex", 0);
        assertThat(s0.get("content")).isEqualTo("even-2");

        @SuppressWarnings("unchecked")
        Map<String, Object> s1 = (Map<String, Object>) projection.get("sample[1]");
        assertThat(s1).containsKeys("inputIndex", "postconditions", "executionTimeMs", "failureDetail");
        assertThat(s1).containsEntry("inputIndex", 1);
        assertThat(((String) s1.get("failureDetail"))).startsWith("odd: got 3");

        @SuppressWarnings("unchecked")
        Map<String, Object> s2 = (Map<String, Object>) projection.get("sample[2]");
        assertThat(s2).containsEntry("inputIndex", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3 = (Map<String, Object>) projection.get("sample[3]");
        assertThat(s3).containsEntry("inputIndex", 1);

        // Diff-anchor comments: one per sample[N] line, snakeyaml-stripped on
        // re-parse so we assert against the raw YAML string.
        long anchorCount = yaml.lines()
                .filter(line -> line.contains("anchor:"))
                .count();
        assertThat(anchorCount).isEqualTo(4L);
    }

    @Test
    @DisplayName("two runs of the same MEASURE produce identical anchor lines — diff aligns")
    void anchorsContentDeterministic() {
        Sampling<NoFactors, Integer, Boolean> sampling1 = Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> ALWAYS_PASSES)
                .inputs(1, 2, 3)
                .samples(3)
                .build();
        Experiment run1 = Experiment.measuring(sampling1, new NoFactors()).build();
        new Engine().run(run1);

        Sampling<NoFactors, Integer, Boolean> sampling2 = Sampling.<NoFactors, Integer, Boolean>builder()
                .useCaseFactory(f -> ALWAYS_PASSES)
                .inputs(1, 2, 3)
                .samples(3)
                .build();
        Experiment run2 = Experiment.measuring(sampling2, new NoFactors()).build();
        new Engine().run(run2);

        Map<String, String> sink1 = new LinkedHashMap<>();
        Map<String, String> sink2 = new LinkedHashMap<>();
        BaselineEmitter.emit(run1, (BiConsumer<String, String>) sink1::put);
        BaselineEmitter.emit(run2, (BiConsumer<String, String>) sink2::put);

        List<String> anchors1 = sink1.values().iterator().next().lines()
                .filter(l -> l.contains("anchor:")).toList();
        List<String> anchors2 = sink2.values().iterator().next().lines()
                .filter(l -> l.contains("anchor:")).toList();
        assertThat(anchors1).hasSize(3).isEqualTo(anchors2);
    }
}
