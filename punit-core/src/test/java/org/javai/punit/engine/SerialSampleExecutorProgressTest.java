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
 * Progress-glyph behavioural contract for {@link SerialSampleExecutor}.
 *
 * <p>Captures {@code System.out} during a controlled sample-loop run
 * and asserts the executor emits the expected glyph stream — one
 * non-newline character per sample, immediately flushed, with the
 * pass/fail discriminant doubly encoded as glyph + colour.
 */
@DisplayName("SerialSampleExecutor — per-sample progress glyphs")
class SerialSampleExecutorProgressTest {

    private static final String GREEN = "[32m";
    private static final String RED = "[31m";
    private static final String RESET = "[0m";

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private String previousColorProperty;

    @BeforeEach
    void redirectStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        previousColorProperty = System.getProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY);
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
        if (previousColorProperty == null) {
            System.clearProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY);
        } else {
            System.setProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY, previousColorProperty);
        }
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
    @DisplayName("default colour mode emits ANSI-wrapped glyphs — green '.' for pass, red 'x' for fail")
    void emitsAnsiWrappedGlyphsByDefault() {
        // Property unset → defaults to true.
        System.clearProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY);

        runWith(new AlternatingUseCase(), List.of(0, 1, 2, 3));

        // 4 samples cycling inputs 0,1,2,3 → pass, fail, pass, fail.
        String expected = GREEN + '.' + RESET
                + RED + 'x' + RESET
                + GREEN + '.' + RESET
                + RED + 'x' + RESET;
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    @DisplayName("punit.progress.color=false disables the ANSI wrapper but keeps the glyphs — "
            + "the pass/fail discriminant survives without colour")
    void disablingColourLeavesGlyphsIntact() {
        System.setProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY, "false");

        runWith(new AlternatingUseCase(), List.of(0, 1, 2, 3));

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .isEqualTo(".x.x")
                .doesNotContain("[")
                .as("no ANSI escape sequences when colour is disabled");
    }

    @Test
    @DisplayName("a defect (use case throws) emits an 'x' glyph regardless of cause")
    void defectEmitsFailureGlyph() {
        System.setProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY, "false");

        UseCase<Void, Integer, String> alwaysThrows = new UseCase<>() {
            @Override public String id() { return "AlwaysThrows"; }
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                throw new RuntimeException("synthetic defect");
            }
        };
        runWith(alwaysThrows, List.of(0, 1));

        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo("xx");
    }

    @Test
    @DisplayName("each glyph is flushed immediately — no buffering across samples")
    void eachGlyphFlushedImmediately() {
        System.setProperty(SerialSampleExecutor.PROGRESS_COLOR_PROPERTY, "false");

        UseCase<Void, Integer, String> alwaysPasses = new UseCase<>() {
            @Override public String id() { return "AlwaysPasses"; }
            @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                // Sample-internal observable: bytes already in the captured
                // stream after this many samples. emitProgress is called
                // *after* the observer.onSample callback, so the glyph
                // for sample N has not been emitted yet here.
                int beforeThisSample = captured.size();
                assertThat(beforeThisSample).isEqualTo(input);
                return Outcome.ok("pass");
            }
        };
        runWith(alwaysPasses, List.of(0, 1, 2, 3));

        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo("....");
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
