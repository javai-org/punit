package org.javai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.MatchResult;
import org.javai.punit.api.PostconditionResult;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SampleClassification — engine's per-sample summary")
class SampleClassificationTest {

    record Factors() {}

    /** Use case stand-in with no max-latency bound. */
    private static final UseCase<Factors, String, Integer> USE_CASE = new UseCase<>() {
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
    };

    private static UseCaseOutcome<String, Integer> outcomeOk(int value, Duration duration, long tokens) {
        return new UseCaseOutcome<>(
                Outcome.ok(value), USE_CASE,
                List.of(), Optional.empty(),
                tokens, duration);
    }

    private static UseCaseOutcome<String, Integer> outcomeOkWith(
            List<PostconditionResult> results, Optional<MatchResult> match,
            Duration duration, long tokens) {
        return new UseCaseOutcome<>(
                Outcome.ok(0), USE_CASE,
                results, match,
                tokens, duration);
    }

    private static UseCaseOutcome<String, Integer> outcomeFail(
            String name, String message, Duration duration, long tokens) {
        return new UseCaseOutcome<>(
                Outcome.fail(name, message), USE_CASE,
                List.of(), Optional.empty(),
                tokens, duration);
    }

    @Nested
    @DisplayName("classify")
    class Classify {

        @Test
        @DisplayName("apply-level Outcome.Fail produces failedAtApply classification")
        void applyFail() {
            var outcome = outcomeFail("llm-error", "boom", Duration.ofMillis(50), 0L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.applyFailed()).isTrue();
            assertThat(c.applyFailureName()).contains("llm-error");
            assertThat(c.applyFailureMessage()).contains("boom");
            assertThat(c.postconditionResults()).isEmpty();
            assertThat(c.match()).isEmpty();
            assertThat(c.durationViolation()).isEmpty();
            assertThat(c.duration()).isEqualTo(Duration.ofMillis(50));
        }

        @Test
        @DisplayName("apply-level Outcome.Ok with no max-latency yields no duration violation")
        void okNoMaxLatency() {
            var outcome = outcomeOk(42, Duration.ofSeconds(5), 100L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.applyFailed()).isFalse();
            assertThat(c.durationViolation()).isEmpty();
            assertThat(c.duration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(c.tokens()).isEqualTo(100L);
        }

        @Test
        @DisplayName("apply-level Outcome.Ok exceeding max-latency surfaces a DurationViolation")
        void okExceedingMaxLatency() {
            UseCase<Factors, String, Integer> bounded = new UseCase<>() {
                @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) { return Outcome.ok(0); }
                @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
                @Override public Optional<Duration> maxLatency() {
                    return Optional.of(Duration.ofMillis(100));
                }
            };
            var outcome = new UseCaseOutcome<>(
                    Outcome.ok(0), bounded, List.<PostconditionResult>of(), Optional.<MatchResult>empty(),
                    0L, Duration.ofMillis(250));

            SampleClassification c = SampleClassification.classify(bounded, outcome);

            assertThat(c.durationViolation()).isPresent();
            assertThat(c.durationViolation().get().observed()).isEqualTo(Duration.ofMillis(250));
            assertThat(c.durationViolation().get().max()).isEqualTo(Duration.ofMillis(100));
        }

        @Test
        @DisplayName("apply-level Outcome.Ok at exactly max-latency does not violate")
        void okAtMaxLatencyExact() {
            UseCase<Factors, String, Integer> bounded = new UseCase<>() {
                @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) { return Outcome.ok(0); }
                @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
                @Override public Optional<Duration> maxLatency() {
                    return Optional.of(Duration.ofMillis(100));
                }
            };
            var outcome = new UseCaseOutcome<>(
                    Outcome.ok(0), bounded, List.<PostconditionResult>of(), Optional.<MatchResult>empty(),
                    0L, Duration.ofMillis(100));

            SampleClassification c = SampleClassification.classify(bounded, outcome);

            assertThat(c.durationViolation()).isEmpty();
        }

        @Test
        @DisplayName("postcondition results are pre-evaluated and surfaced unchanged")
        void postconditionResultsCarried() {
            var results = List.of(
                    PostconditionResult.passed("first"),
                    PostconditionResult.failed("second", "tripped"));
            var outcome = outcomeOkWith(results, Optional.empty(), Duration.ofMillis(20), 5L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.postconditionResults()).hasSize(2);
            assertThat(c.postconditionResults().get(1).failed()).isTrue();
        }

        @Test
        @DisplayName("match status is forwarded from the outcome")
        void matchForwarded() {
            var match = MatchResult.fail("equals", 1, 2, "1 != 2");
            var outcome = outcomeOkWith(List.of(), Optional.of(match), Duration.ofMillis(1), 0L);

            SampleClassification c = SampleClassification.classify(USE_CASE, outcome);

            assertThat(c.match()).contains(match);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("negative tokens are rejected")
        void rejectsNegativeTokens() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> SampleClassification.failedAtApply(
                            "x", "y", Duration.ZERO, -1L));
        }

        @Test
        @DisplayName("negative duration is rejected")
        void rejectsNegativeDuration() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> SampleClassification.failedAtApply(
                            "x", "y", Duration.ofMillis(-1), 0L));
        }

        @Test
        @DisplayName("postcondition list is defensively copied")
        void postconditionResultsCopied() {
            var mutable = new java.util.ArrayList<PostconditionResult>();
            mutable.add(PostconditionResult.passed("a"));

            SampleClassification c = SampleClassification.from(
                    mutable, Optional.empty(), Optional.empty(),
                    Duration.ZERO, 0L);

            mutable.add(PostconditionResult.failed("b", "intruder"));

            assertThat(c.postconditionResults()).hasSize(1);
        }
    }
}
