package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseOutcome — recipient-facing artefact assembled by Contract.apply")
class UseCaseOutcomeTest {

    /** Stand-in contract used in the canonical-constructor tests. */
    private static final Contract<String, Integer> CONTRACT = new Contract<>() {
        @Override
        public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }

        @Override
        public void postconditions(ContractBuilder<Integer> b) { /* none */ }
    };

    @Test
    @DisplayName("canonical constructor copies the postcondition list")
    void postconditionResultsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<PostconditionResult>();
        mutable.add(PostconditionResult.passed("a"));

        var outcome = new UseCaseOutcome<>(
                Outcome.ok(5), CONTRACT, mutable, Optional.empty(), 0L, Duration.ZERO);

        mutable.add(PostconditionResult.failed("b", "intruder"));   // mutate after

        assertThat(outcome.postconditionResults()).hasSize(1);
        assertThat(outcome.postconditionResults().get(0).description()).isEqualTo("a");
    }

    @Test
    @DisplayName("negative tokens are rejected")
    void rejectsNegativeTokens() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(), Optional.empty(),
                        -1L, Duration.ZERO));
    }

    @Test
    @DisplayName("negative duration is rejected")
    void rejectsNegativeDuration() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(), Optional.empty(),
                        0L, Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("null fields are rejected")
    void rejectsNullFields() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        null, CONTRACT, List.of(), Optional.empty(), 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), null, List.of(), Optional.empty(), 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), CONTRACT, null, Optional.empty(), 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(), null, 0L, Duration.ZERO));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new UseCaseOutcome<>(
                        Outcome.ok(1), CONTRACT, List.of(), Optional.empty(), 0L, null));
    }

    @Test
    @DisplayName("value() returns the apply-level Fail short-circuiting everything else")
    void valueReturnsApplyLevelFail() {
        Outcome<Integer> applyFail = Outcome.fail("apply-error", "boom");
        var outcome = new UseCaseOutcome<>(
                applyFail, CONTRACT,
                List.of(PostconditionResult.passed("would-pass")),
                Optional.empty(), 0L, Duration.ZERO);

        assertThat(outcome.value()).isSameAs(applyFail);
    }

    @Test
    @DisplayName("value() returns instance_conformance Fail when the match mismatched")
    void valueReturnsMatchFail() {
        var outcome = new UseCaseOutcome<>(
                Outcome.ok(5), CONTRACT,
                List.of(),
                Optional.of(MatchResult.fail(
                        "exact", 6, 5, "expected 6 got 5")),
                0L, Duration.ZERO);

        assertThat(outcome.value().isFail()).isTrue();
        assertThat(((Outcome.Fail<Integer>) outcome.value()).failure().id().name())
                .isEqualTo("instance_conformance");
    }

    @Test
    @DisplayName("value() returns the first postcondition failure")
    void valueReturnsFirstClauseFail() {
        var outcome = new UseCaseOutcome<>(
                Outcome.ok(5), CONTRACT,
                List.of(
                        PostconditionResult.passed("first ok"),
                        PostconditionResult.failed("second-clause", "tripped"),
                        PostconditionResult.failed("third-clause", "would also trip")),
                Optional.empty(), 0L, Duration.ZERO);

        assertThat(outcome.value().isFail()).isTrue();
        assertThat(((Outcome.Fail<Integer>) outcome.value()).failure().message())
                .contains("tripped");
    }

    @Test
    @DisplayName("value() returns Ok when result is Ok and nothing else fails")
    void valueReturnsOkWhenAllPass() {
        var outcome = new UseCaseOutcome<>(
                Outcome.ok(5), CONTRACT,
                List.of(PostconditionResult.passed("all good")),
                Optional.of(MatchResult.pass("exact", 5, 5)),
                12L, Duration.ofMillis(7));

        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(5);
    }
}
