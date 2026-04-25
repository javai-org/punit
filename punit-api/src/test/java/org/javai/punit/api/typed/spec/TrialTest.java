package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trial + SampleSummary.trials")
class TrialTest {

    @Test
    @DisplayName("Trial round-trips its components")
    void trialRoundTrip() {
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(42);
        Trial<String, Integer> trial = new Trial<>("hello", outcome, Duration.ofMillis(7));

        assertThat(trial.input()).isEqualTo("hello");
        assertThat(trial.outcome()).isSameAs(outcome);
        assertThat(trial.duration()).isEqualTo(Duration.ofMillis(7));
    }

    @Test
    @DisplayName("Trial accepts a null input — input is the only nullable component")
    void trialAllowsNullInput() {
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(42);
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
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(42);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new Trial<>("hello", outcome, null));
    }

    @Test
    @DisplayName("SampleSummary.trials() returns the supplied list")
    void summaryTrialsAccessor() {
        UseCaseOutcome<Integer> ok = UseCaseOutcome.ok(1);
        UseCaseOutcome<Integer> ok2 = UseCaseOutcome.ok(2);
        List<Trial<?, Integer>> trials = List.of(
                new Trial<String, Integer>("a", ok, Duration.ofMillis(5)),
                new Trial<String, Integer>("b", ok2, Duration.ofMillis(7)));

        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(ok, ok2),
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
        UseCaseOutcome<Integer> ok = UseCaseOutcome.ok(1);
        List<Trial<?, Integer>> oneTrial = List.of(
                new Trial<String, Integer>("a", ok, Duration.ofMillis(5)));

        // successes + failures = 2, but trials list has 1 entry
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SampleSummary<>(
                        List.of(ok, ok),
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
        UseCaseOutcome<Integer> ok = UseCaseOutcome.ok(1);
        SampleSummary<Integer> summary = new SampleSummary<>(
                List.of(ok),
                Duration.ofMillis(5),
                1, 0, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                List.of());

        assertThat(summary.trials()).isEmpty();
    }
}
