package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseOutcome")
class UseCaseOutcomeTest {

    @Test
    @DisplayName("ok() wraps a successful Outcome around the value")
    void okWrapsValue() {
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(42);
        assertThat(outcome.value()).isInstanceOf(Outcome.Ok.class);
        assertThat(outcome.value().getOrThrow()).isEqualTo(42);
        assertThat(outcome.evaluatePostconditions()).isEmpty();
    }

    @Test
    @DisplayName("fail() wraps a failure Outcome with the given name and message")
    void failWrapsFailure() {
        UseCaseOutcome<String> outcome = UseCaseOutcome.fail("bad_input", "empty string");
        assertThat(outcome.value().isFail()).isTrue();
        assertThat(outcome.evaluatePostconditions()).hasSize(1);
        assertThat(outcome.evaluatePostconditions().get(0).failureMessage())
                .contains("empty string");
    }

    @Test
    @DisplayName("tokens default to zero and are record-accessible")
    void tokensDefaultZero() {
        assertThat(UseCaseOutcome.ok(1).tokens()).isZero();
        assertThat(UseCaseOutcome.fail("x", "y").tokens()).isZero();
    }

    @Test
    @DisplayName("withTokens attaches a token cost to the outcome")
    void withTokensPropagates() {
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(1).withTokens(256);
        assertThat(outcome.tokens()).isEqualTo(256L);
        // Value / postconditions are preserved across withTokens
        assertThat(outcome.value().getOrThrow()).isEqualTo(1);
    }

    @Test
    @DisplayName("negative tokens are rejected")
    void rejectsNegativeTokens() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UseCaseOutcome.ok(1).withTokens(-1));
    }

    @Test
    @DisplayName("postconditions are evaluated lazily and re-evaluated on each call")
    void lazyPostconditionEvaluation() {
        AtomicInteger evalCount = new AtomicInteger();
        PostconditionEvaluator<Integer> evaluator = new PostconditionEvaluator<>() {
            @Override public List<PostconditionResult> evaluate(Integer result) {
                evalCount.incrementAndGet();
                return List.of(PostconditionResult.passed("non-negative"));
            }
            @Override public int postconditionCount() { return 1; }
        };
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.of(3, evaluator);

        assertThat(evalCount).hasValue(0);

        outcome.value();
        outcome.evaluatePostconditions();
        outcome.value();

        // Each call re-evaluates — no caching at the outcome level
        assertThat(evalCount).hasValue(3);
    }

    @Test
    @DisplayName("value() returns Fail when any postcondition fails, preserving the reason")
    void valueReflectsPostconditionFailure() {
        PostconditionEvaluator<Integer> evaluator = new PostconditionEvaluator<>() {
            @Override public List<PostconditionResult> evaluate(Integer result) {
                return List.of(
                        PostconditionResult.passed("is integer"),
                        PostconditionResult.failed("positive", "got " + result));
            }
            @Override public int postconditionCount() { return 2; }
        };
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.of(-5, evaluator);

        assertThat(outcome.value().isFail()).isTrue();
        assertThat(((Outcome.Fail<Integer>) outcome.value()).failure().message())
                .contains("got -5");
    }

    @Test
    @DisplayName("duration defaults to Duration.ZERO for the convenience factories")
    void durationDefaultsZero() {
        assertThat(UseCaseOutcome.ok(1).duration()).isEqualTo(Duration.ZERO);
        assertThat(UseCaseOutcome.fail("x", "y").duration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("withDuration attaches a measured duration to the outcome")
    void withDurationPropagates() {
        Duration d = Duration.ofMillis(125);
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.ok(1).withDuration(d);
        assertThat(outcome.duration()).isEqualTo(d);
        // Value / tokens preserved across withDuration
        assertThat(outcome.value().getOrThrow()).isEqualTo(1);
        assertThat(outcome.tokens()).isZero();
    }

    @Test
    @DisplayName("negative duration is rejected")
    void rejectsNegativeDuration() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UseCaseOutcome.ok(1).withDuration(Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("value() returns Ok when all postconditions pass, wrapping the raw result")
    void valueOkWhenAllPass() {
        PostconditionEvaluator<Integer> evaluator = new PostconditionEvaluator<>() {
            @Override public List<PostconditionResult> evaluate(Integer result) {
                return List.of(PostconditionResult.passed("non-null"));
            }
            @Override public int postconditionCount() { return 1; }
        };
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.of(7, evaluator);

        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(7);
    }
}
