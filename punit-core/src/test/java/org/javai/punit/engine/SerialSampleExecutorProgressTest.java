package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseOutcome;
import org.javai.punit.api.spec.SampleObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Progress-counter behavioural contract for {@link SerialSampleExecutor}.
 *
 * <p>Captures {@code System.out} during a controlled sample-loop run
 * and asserts the executor emits a per-sample {@code "\r m/n"}
 * counter — one event per sample, immediately flushed, width-padded
 * for stable in-place updates outside Gradle.
 */
@DisplayName("SerialSampleExecutor — per-sample progress counter")
class SerialSampleExecutorProgressTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    /** Use case whose outcome alternates pass/fail by input parity. */
    private static final class AlternatingUseCase implements UseCase<Void, Integer, String> {
        @Override public String id() { return "Alternating"; }
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
        @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
            return input % 2 == 0
                    ? Outcome.ok("even-" + input)
                    : Outcome.fail("odd", "input was " + input);
        }
    }

    @Test
    @DisplayName("emits a \\r-prefixed m/n counter after every sample, regardless of pass/fail")
    void emitsCounterPerSample() {
        runWith(new AlternatingUseCase(), List.of(0, 1, 2, 3));

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .as("4 samples → 4 counter emissions: 1/4, 2/4, 3/4, 4/4")
                .isEqualTo("\r1/4\r2/4\r3/4\r4/4");
    }

    @Test
    @DisplayName("width-pads the numerator to the decimal width of the total — keeps in-place "
            + "updates aligned for terminals that honour \\r")
    void widthPadsNumerator() {
        runWith(new AlternatingUseCase(), List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        // total = 11 → width = 2; numerator padded with one leading space for single digits.
        String expected = "\r 1/11\r 2/11\r 3/11\r 4/11\r 5/11\r 6/11\r 7/11"
                + "\r 8/11\r 9/11\r10/11\r11/11";
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a defect (use case throws) still advances the counter")
    void defectAlsoAdvancesCounter() {
        UseCase<Void, Integer, String> alwaysThrows = new UseCase<>() {
            @Override public String id() { return "AlwaysThrows"; }
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                throw new RuntimeException("synthetic defect");
            }
        };
        runWith(alwaysThrows, List.of(0, 1));

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .as("defect path emits the same counter format as the normal path")
                .isEqualTo("\r1/2\r2/2");
    }

    @Test
    @DisplayName("each emission is flushed before the next sample begins — no buffering across "
            + "samples, which is the property the feature exists to provide")
    void eachEmissionFlushedImmediately() {
        UseCase<Void, Integer, String> counterPeek = new UseCase<>() {
            @Override public String id() { return "CounterPeek"; }
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                // Sample-internal observable: bytes already in the captured
                // stream when this sample starts. emitProgress is called
                // *after* observer.onSample, so for sample i (0-indexed)
                // we expect i prior counter emissions, each of length
                // 1 + width + 1 + 1 = 4 (e.g. "\r1/4" for total=4).
                int priorEmissions = input;
                int emissionLength = 1 + 1 + 1 + 1; // \r + digit + / + digit
                assertThat(captured.size()).isEqualTo(priorEmissions * emissionLength);
                return Outcome.ok("pass");
            }
        };
        runWith(counterPeek, List.of(0, 1, 2, 3));

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .isEqualTo("\r1/4\r2/4\r3/4\r4/4");
    }

    private void runWith(UseCase<Void, Integer, String> useCase, List<Integer> inputs) {
        new SerialSampleExecutor().runSamples(
                useCase,
                inputs,
                List.of(),
                Optional.empty(),
                inputs.size(),
                0,
                new IgnoringObserver(),
                () -> false);
    }

    /** Observer that records nothing — the test asserts on stdout only. */
    private static final class IgnoringObserver implements SampleObserver<String> {
        @Override
        public void onSample(int index, UseCaseOutcome<?, String> outcome, Duration elapsed) { }

        @Override
        public void onDefect(int index, Throwable throwable, Duration elapsed) { }
    }
}
