package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.javai.punit.api.FactorArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for CyclingFactorIterator.
 */
@DisplayName("CyclingFactorIterator")
class CyclingFactorIteratorTest {

    private static final List<FactorArguments> THREE_FACTORS = List.of(
            FactorArguments.of("headphones"),
            FactorArguments.of("laptop"),
            FactorArguments.of("keyboard")
    );

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create iterator with valid parameters")
        void shouldCreateWithValidParameters() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 10);

            assertThat(iterator.getTotalSamples()).isEqualTo(10);
            assertThat(iterator.getFactorCount()).isEqualTo(3);
            assertThat(iterator.getCurrentIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject null factors")
        void shouldRejectNullFactors() {
            assertThatThrownBy(() -> new CyclingFactorIterator(null, 10))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject empty factors")
        void shouldRejectEmptyFactors() {
            assertThatThrownBy(() -> new CyclingFactorIterator(List.of(), 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should reject negative sample count")
        void shouldRejectNegativeSampleCount() {
            assertThatThrownBy(() -> new CyclingFactorIterator(THREE_FACTORS, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should accept zero sample count")
        void shouldAcceptZeroSampleCount() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 0);

            assertThat(iterator.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Cycling Behavior")
    class CyclingBehavior {

        @Test
        @DisplayName("should cycle through factors when samples > factor count")
        void shouldCycleThroughFactors() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 7);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            assertThat(queries).containsExactly(
                    "headphones", "laptop", "keyboard",  // First cycle
                    "headphones", "laptop", "keyboard",  // Second cycle
                    "headphones"                          // Partial third cycle
            );
        }

        @Test
        @DisplayName("should not cycle when samples <= factor count")
        void shouldNotCycleWhenSamplesLessThanFactors() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 2);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            assertThat(queries).containsExactly("headphones", "laptop");
        }

        @Test
        @DisplayName("should return each factor exactly once when samples equals factor count")
        void shouldReturnEachFactorOnceWhenSamplesEqualsFactors() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 3);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            assertThat(queries).containsExactly("headphones", "laptop", "keyboard");
        }

        @Test
        @DisplayName("should handle single-factor list")
        void shouldHandleSingleFactorList() {
            List<FactorArguments> singleFactor = List.of(FactorArguments.of("headphones"));
            CyclingFactorIterator iterator = new CyclingFactorIterator(singleFactor, 5);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            // All 5 samples should get the same factor
            assertThat(queries).containsExactly(
                    "headphones", "headphones", "headphones", "headphones", "headphones"
            );
        }
    }

    @Nested
    @DisplayName("Iterator Protocol")
    class IteratorProtocol {

        @Test
        @DisplayName("hasNext should return true before samples exhausted")
        void hasNextShouldReturnTrueBeforeExhausted() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 3);

            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.hasNext()).isFalse();
        }

        @Test
        @DisplayName("next should throw when samples exhausted")
        void nextShouldThrowWhenExhausted() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 1);
            iterator.next();

            assertThatThrownBy(iterator::next)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("getCurrentIndex should track position")
        void getCurrentIndexShouldTrackPosition() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 5);

            assertThat(iterator.getCurrentIndex()).isEqualTo(0);
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(1);
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(2);
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("should reset to beginning")
        void shouldResetToBeginning() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 5);

            // Consume some elements
            iterator.next();
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(2);

            // Reset
            iterator.reset();
            assertThat(iterator.getCurrentIndex()).isEqualTo(0);
            assertThat(iterator.hasNext()).isTrue();
        }

        @Test
        @DisplayName("should allow re-iteration after reset")
        void shouldAllowReIterationAfterReset() {
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 2);

            // First iteration
            String first1 = (String) iterator.next().get(0);
            String second1 = (String) iterator.next().get(0);

            // Reset and iterate again
            iterator.reset();
            String first2 = (String) iterator.next().get(0);
            String second2 = (String) iterator.next().get(0);

            assertThat(first1).isEqualTo(first2).isEqualTo("headphones");
            assertThat(second1).isEqualTo(second2).isEqualTo("laptop");
        }
    }

    @Nested
    @DisplayName("Usage Statistics")
    class UsageStatistics {

        @Test
        @DisplayName("should compute approximate usage per factor")
        void shouldComputeApproximateUsagePerFactor() {
            // 10 samples / 3 factors = ~3.33, rounded up = 4
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 10);

            assertThat(iterator.getApproximateUsagePerFactor()).isEqualTo(4);
        }

        @Test
        @DisplayName("should compute exact usage when evenly divisible")
        void shouldComputeExactUsageWhenEvenlyDivisible() {
            // 9 samples / 3 factors = exactly 3
            CyclingFactorIterator iterator = new CyclingFactorIterator(THREE_FACTORS, 9);

            assertThat(iterator.getApproximateUsagePerFactor()).isEqualTo(3);
        }
    }
}

