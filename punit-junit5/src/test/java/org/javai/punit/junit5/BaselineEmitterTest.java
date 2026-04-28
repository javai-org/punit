package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;

import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.Experiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BaselineEmitter — defect-on-misuse contract")
class BaselineEmitterTest {

    record NoFactors() { }

    private static final UseCase<NoFactors, Integer, Boolean> ALWAYS_PASSES = new UseCase<>() {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.ok(true);
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
                .stepper((current, history) -> history.size() >= 1 ? null : new NoFactors())
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
