package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseOutcome")
class UseCaseOutcomeTest {

    @Test
    @DisplayName("of() wraps the value")
    void ofWrapsValue() {
        assertThat(UseCaseOutcome.of(42).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("of(null) is rejected")
    void ofRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> UseCaseOutcome.of(null));
    }

    @Test
    @DisplayName("equality is by value")
    void equalityByValue() {
        assertThat(UseCaseOutcome.of("a")).isEqualTo(UseCaseOutcome.of("a"));
        assertThat(UseCaseOutcome.of("a")).isNotEqualTo(UseCaseOutcome.of("b"));
    }
}
