package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PreconditionException")
class PreconditionExceptionTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates exception with description and input")
        void createsExceptionWithDescriptionAndInput() {
            Object input = new Object();
            PreconditionException exception =
                    new PreconditionException("Input must not be null", input);

            assertThat(exception.getPreconditionDescription()).isEqualTo("Input must not be null");
            assertThat(exception.getInput()).isSameAs(input);
            assertThat(exception.getMessage()).isEqualTo("Precondition violated: Input must not be null");
        }

        @Test
        @DisplayName("allows null input")
        void allowsNullInput() {
            PreconditionException exception =
                    new PreconditionException("Input must not be null", null);

            assertThat(exception.getInput()).isNull();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new PreconditionException(null, new Object()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("preconditionDescription must not be null");
        }
    }

    @Nested
    @DisplayName("exception behavior")
    class ExceptionBehaviorTests {

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            PreconditionException exception =
                    new PreconditionException("test", null);

            assertThat(exception).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("can be thrown and caught")
        void canBeThrownAndCaught() {
            assertThatThrownBy(() -> {
                throw new PreconditionException("Model must be specified", "invalid-input");
            })
                    .isInstanceOf(PreconditionException.class)
                    .hasMessageContaining("Model must be specified")
                    .satisfies(e -> {
                        PreconditionException ex = (PreconditionException) e;
                        assertThat(ex.getInput()).isEqualTo("invalid-input");
                    });
        }
    }
}
