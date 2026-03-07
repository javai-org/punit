package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for SequentialFactorIterator.
 */
@DisplayName("SequentialFactorIterator")
class SequentialFactorIteratorTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create iterator with valid parameters")
        void shouldCreateWithValidParameters() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("headphones"),
                    FactorArguments.of("laptop")
            );

            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 2);

            assertThat(iterator.getTotalSamples()).isEqualTo(2);
            assertThat(iterator.getCurrentIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject null stream")
        void shouldRejectNullStream() {
            assertThatThrownBy(() -> new SequentialFactorIterator(null, 10))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject negative sample count")
        void shouldRejectNegativeSampleCount() {
            Stream<FactorArguments> stream = Stream.of(FactorArguments.of("test"));

            assertThatThrownBy(() -> new SequentialFactorIterator(stream, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should accept zero sample count")
        void shouldAcceptZeroSampleCount() {
            Stream<FactorArguments> stream = Stream.of(FactorArguments.of("test"));
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 0);

            assertThat(iterator.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Sequential Consumption")
    class SequentialConsumption {

        @Test
        @DisplayName("should consume elements in order")
        void shouldConsumeElementsInOrder() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second"),
                    FactorArguments.of("third")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 3);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            assertThat(queries).containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("should stop after requested samples even if stream has more")
        void shouldStopAfterRequestedSamples() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second"),
                    FactorArguments.of("third"),
                    FactorArguments.of("fourth"),
                    FactorArguments.of("fifth")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 3);
            List<String> queries = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                queries.add((String) args.get(0));
            }

            assertThat(queries).containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("should work with infinite stream")
        void shouldWorkWithInfiniteStream() {
            AtomicInteger counter = new AtomicInteger(0);
            Stream<FactorArguments> infiniteStream = Stream.generate(
                    () -> FactorArguments.of(counter.incrementAndGet())
            );

            SequentialFactorIterator iterator = new SequentialFactorIterator(infiniteStream, 5);
            List<Integer> indices = new ArrayList<>();

            while (iterator.hasNext()) {
                FactorArguments args = iterator.next();
                indices.add((Integer) args.get(0));
            }

            assertThat(indices).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Nested
    @DisplayName("Stream Exhaustion")
    class StreamExhaustion {

        @Test
        @DisplayName("should throw when stream exhausted before samples complete")
        void shouldThrowWhenStreamExhausted() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 5);

            // Consume available elements
            iterator.next();
            iterator.next();

            // Third element should fail - stream exhausted
            assertThatThrownBy(iterator::next)
                    .isInstanceOf(SequentialFactorIterator.StreamExhaustedException.class)
                    .hasMessageContaining("exhausted")
                    .hasMessageContaining("2 elements")
                    .hasMessageContaining("5 samples");
        }

        @Test
        @DisplayName("exception message should be helpful")
        void exceptionMessageShouldBeHelpful() {
            Stream<FactorArguments> stream = Stream.of(FactorArguments.of("only one"));
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 100);

            iterator.next();

            assertThatThrownBy(iterator::next)
                    .isInstanceOf(SequentialFactorIterator.StreamExhaustedException.class)
                    .hasMessageContaining("provide more factors")
                    .hasMessageContaining("reduce the sample count");
        }
    }

    @Nested
    @DisplayName("Iterator Protocol")
    class IteratorProtocol {

        @Test
        @DisplayName("hasNext should return true before samples exhausted")
        void hasNextShouldReturnTrueBeforeExhausted() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 2);

            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.hasNext()).isTrue();
            iterator.next();
            assertThat(iterator.hasNext()).isFalse();
        }

        @Test
        @DisplayName("next should throw when samples exhausted")
        void nextShouldThrowWhenExhausted() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 1);
            iterator.next();

            assertThatThrownBy(iterator::next)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("getCurrentIndex should track position")
        void getCurrentIndexShouldTrackPosition() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second"),
                    FactorArguments.of("third")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 3);

            assertThat(iterator.getCurrentIndex()).isEqualTo(0);
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(1);
            iterator.next();
            assertThat(iterator.getCurrentIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("getRemainingSamples should decrease")
        void getRemainingSamplesShouldDecrease() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first"),
                    FactorArguments.of("second"),
                    FactorArguments.of("third")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 3);

            assertThat(iterator.getRemainingSamples()).isEqualTo(3);
            iterator.next();
            assertThat(iterator.getRemainingSamples()).isEqualTo(2);
            iterator.next();
            assertThat(iterator.getRemainingSamples()).isEqualTo(1);
            iterator.next();
            assertThat(iterator.getRemainingSamples()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Close Behavior")
    class CloseBehavior {

        @Test
        @DisplayName("should reject next after close")
        void shouldRejectNextAfterClose() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 1);

            iterator.close();

            assertThatThrownBy(iterator::next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("hasNext should return false after close")
        void hasNextShouldReturnFalseAfterClose() {
            Stream<FactorArguments> stream = Stream.of(
                    FactorArguments.of("first")
            );
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 1);

            iterator.close();

            assertThat(iterator.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Memory Efficiency")
    class MemoryEfficiency {

        @Test
        @DisplayName("should not materialize stream elements")
        void shouldNotMaterializeStreamElements() {
            AtomicInteger generationCount = new AtomicInteger(0);
            Stream<FactorArguments> stream = Stream.generate(() -> {
                generationCount.incrementAndGet();
                return FactorArguments.of("index", generationCount.get());
            });

            // Create iterator but don't consume
            SequentialFactorIterator iterator = new SequentialFactorIterator(stream, 1000);

            // Nothing should be generated yet
            assertThat(generationCount.get()).isEqualTo(0);

            // Consume one element
            iterator.next();
            assertThat(generationCount.get()).isEqualTo(1);

            // Consume another
            iterator.next();
            assertThat(generationCount.get()).isEqualTo(2);
        }
    }
}

