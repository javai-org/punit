package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
        assertThat(((Outcome.Ok<Integer>) outcome.value()).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("fail() wraps a failure Outcome with the given name and message")
    void failWrapsFailure() {
        UseCaseOutcome<String> outcome = UseCaseOutcome.fail("bad_input", "empty string");
        assertThat(outcome.value()).isInstanceOf(Outcome.Fail.class);
        assertThat(outcome.value().isFail()).isTrue();
    }

    @Test
    @DisplayName("of() wraps an already-constructed Outcome")
    void ofWrapsOutcome() {
        Outcome<Integer> out = Outcome.ok(7);
        UseCaseOutcome<Integer> wrapped = UseCaseOutcome.of(out);
        assertThat(wrapped.value()).isSameAs(out);
    }

    @Test
    @DisplayName("ok() rejects null")
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> UseCaseOutcome.of(null));
    }

    @Test
    @DisplayName("equality is structural via the wrapped Outcome")
    void equalityByValue() {
        assertThat(UseCaseOutcome.ok(1)).isEqualTo(UseCaseOutcome.ok(1));
        assertThat(UseCaseOutcome.ok(1)).isNotEqualTo(UseCaseOutcome.ok(2));
    }
}
