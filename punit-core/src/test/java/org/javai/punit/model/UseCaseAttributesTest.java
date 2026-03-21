package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseAttributes")
class UseCaseAttributesTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void defaultAttributes() {
            assertThat(UseCaseAttributes.DEFAULT.warmup()).isEqualTo(0);
            assertThat(UseCaseAttributes.DEFAULT.maxConcurrent()).isEqualTo(1);
            assertThat(UseCaseAttributes.DEFAULT.hasWarmup()).isFalse();
            assertThat(UseCaseAttributes.DEFAULT.hasMaxConcurrent()).isFalse();
        }

        @Test
        void warmupOnly() {
            var attrs = new UseCaseAttributes(5);
            assertThat(attrs.warmup()).isEqualTo(5);
            assertThat(attrs.maxConcurrent()).isEqualTo(1);
            assertThat(attrs.hasWarmup()).isTrue();
            assertThat(attrs.hasMaxConcurrent()).isFalse();
        }

        @Test
        void bothAttributes() {
            var attrs = new UseCaseAttributes(3, 4);
            assertThat(attrs.warmup()).isEqualTo(3);
            assertThat(attrs.maxConcurrent()).isEqualTo(4);
            assertThat(attrs.hasWarmup()).isTrue();
            assertThat(attrs.hasMaxConcurrent()).isTrue();
        }

        @Test
        void negativeWarmupThrows() {
            assertThatThrownBy(() -> new UseCaseAttributes(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("warmup");
        }

        @Test
        void zeroMaxConcurrentThrows() {
            assertThatThrownBy(() -> new UseCaseAttributes(0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrent");
        }

        @Test
        void negativeMaxConcurrentThrows() {
            assertThatThrownBy(() -> new UseCaseAttributes(0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrent");
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        void sameValuesAreEqual() {
            var a = new UseCaseAttributes(5, 2);
            var b = new UseCaseAttributes(5, 2);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void differentValuesAreNotEqual() {
            var a = new UseCaseAttributes(5, 2);
            var b = new UseCaseAttributes(5, 3);
            assertThat(a).isNotEqualTo(b);
        }
    }
}
