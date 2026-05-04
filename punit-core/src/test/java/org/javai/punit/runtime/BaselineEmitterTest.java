package org.javai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.NextFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BaselineEmitter — defect-on-misuse contract")
class BaselineEmitterTest {

    record NoFactors() { }

    private static final UseCase<NoFactors, Integer, Boolean> ALWAYS_PASSES = new UseCase<>() {
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
}
