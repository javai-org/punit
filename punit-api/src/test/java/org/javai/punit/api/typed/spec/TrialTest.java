package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.typed.Contract;
import org.javai.punit.api.typed.ContractBuilder;
import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.TokenTracker;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trial + SampleSummary.trials")
class TrialTest {

    /** Stand-in contract for outcome construction. */
    private static final Contract<String, Integer> CONTRACT = new Contract<>() {
        @Override
        public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }

        @Override
        public void postconditions(ContractBuilder<Integer> b) { /* none */ }
    };

    private static UseCaseOutcome<String, Integer> ok(int value) {
        return new UseCaseOutcome<>(
                Outcome.ok(value), CONTRACT,
                List.of(), Optional.empty(),
                0L, Duration.ZERO);
    }

    @Test
    @DisplayName("Trial round-trips its components")
    void trialRoundTrip() {
        UseCaseOutcome<String, Integer> outcome = ok(42);
        Trial<String, Integer> trial = new Trial<>("hello", outcome, Duration.ofMillis(7));

        assertThat(trial.input()).isEqualTo("hello");
        assertThat(trial.outcome()).isSameAs(outcome);
        assertThat(trial.duration()).isEqualTo(Duration.ofMillis(7));
    }

    @Test
    @DisplayName("Trial accepts a null input — input is the only nullable component")
    void trialAllowsNullInput() {
        UseCaseOutcome<String, Integer> outcome = ok(42);
        Trial<String, Integer> trial = new Trial<>(null, outcome, Duration.ZERO);
        assertThat(trial.input()).isNull();
    }

    @Test
    @DisplayName("Trial rejects a null outcome")
    void trialRejectsNullOutcome() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new Trial<String, Integer>("hello", null, Duration.ZERO));
    }

    @Test
    @DisplayName("Trial rejects a null duration")
    void trialRejectsNullDuration() {
        UseCaseOutcome<String, Integer> outcome = ok(42);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new Trial<>("hello", outcome, null));
    }

    @Test
    @DisplayName("SampleSummary.trials() returns the supplied list")
    void summaryTrialsAccessor() {
        UseCaseOutcome<String, Integer> ok1 = ok(1);
        UseCaseOutcome<String, Integer> ok2 = ok(2);
        List<Trial<?, Integer>> trials = List.of(
                new Trial<>("a", ok1, Duration.ofMillis(5)),
                new Trial<>("b", ok2, Duration.ofMillis(7)));

        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(ok1, ok2),
                Duration.ofMillis(12),
                2, 0, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                trials);

        assertThat(summary.trials()).hasSize(2);
        assertThat(summary.trials().get(0).input()).isEqualTo("a");
        assertThat(summary.trials().get(1).input()).isEqualTo("b");
    }

    @Test
    @DisplayName("SampleSummary rejects a trials list whose size does not match successes + failures")
    void summaryRejectsMismatchedTrialCount() {
        UseCaseOutcome<String, Integer> okOutcome = ok(1);
        List<Trial<?, Integer>> oneTrial = List.of(
                new Trial<>("a", okOutcome, Duration.ofMillis(5)));

        // successes + failures = 2, but trials list has 1 entry
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SampleSummary<>(
                        List.of(okOutcome, okOutcome),
                        Duration.ofMillis(12),
                        2, 0, 0L, 0,
                        LatencyResult.empty(),
                        TerminationReason.COMPLETED,
                        oneTrial))
                .withMessageContaining("trials");
    }

    @Test
    @DisplayName("SampleSummary accepts an empty trials list (back-compat path)")
    void summaryAcceptsEmptyTrials() {
        UseCaseOutcome<String, Integer> okOutcome = ok(1);
        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(okOutcome),
                Duration.ofMillis(5),
                1, 0, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                List.of());

        assertThat(summary.trials()).isEmpty();
    }
}
