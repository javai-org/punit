package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Precondition")
class PreconditionTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates precondition with description and predicate")
        void createsPrecondition() {
            Precondition<String> precondition = new Precondition<>(
                    "Not empty", s -> !s.isEmpty());

            assertThat(precondition.description()).isEqualTo("Not empty");
            assertThat(precondition.predicate()).isNotNull();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new Precondition<String>(null, s -> true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Precondition<String>("   ", s -> true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }

        @Test
        @DisplayName("throws when predicate is null")
        void throwsWhenPredicateIsNull() {
            assertThatThrownBy(() -> new Precondition<String>("Valid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("predicate must not be null");
        }
    }

    @Nested
    @DisplayName("check()")
    class CheckTests {

        @Test
        @DisplayName("does not throw when predicate returns true")
        void doesNotThrowWhenPasses() {
            Precondition<String> precondition = new Precondition<>(
                    "Not empty", s -> !s.isEmpty());

            assertThatCode(() -> precondition.check("hello"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws PreconditionException when predicate returns false")
        void throwsWhenFails() {
            Precondition<String> precondition = new Precondition<>(
                    "Not empty", s -> !s.isEmpty());

            assertThatThrownBy(() -> precondition.check(""))
                    .isInstanceOf(PreconditionException.class)
                    .satisfies(e -> {
                        PreconditionException ex = (PreconditionException) e;
                        assertThat(ex.getPreconditionDescription()).isEqualTo("Not empty");
                        assertThat(ex.getInput()).isEqualTo("");
                    });
        }

        @Test
        @DisplayName("throws PreconditionException when predicate throws")
        void throwsWhenPredicateThrows() {
            Precondition<String> precondition = new Precondition<>(
                    "Has length", s -> s.length() > 0);

            assertThatThrownBy(() -> precondition.check(null))
                    .isInstanceOf(PreconditionException.class)
                    .hasMessageContaining("Has length")
                    .hasMessageContaining("evaluation failed");
        }
    }
}
