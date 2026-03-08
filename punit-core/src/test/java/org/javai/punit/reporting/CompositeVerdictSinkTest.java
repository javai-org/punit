package org.javai.punit.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompositeVerdictSinkTest {

    private static VerdictEvent sampleEvent() {
        return new VerdictEvent(
                "v:abc123", "testMethod", "useCase1", true,
                Map.of(), Map.of(), Instant.now());
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects null sinks list")
        void rejectsNullSinks() {
            assertThatThrownBy(() -> new CompositeVerdictSink(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("reports correct size")
        void reportsCorrectSize() {
            VerdictSink s1 = event -> {};
            VerdictSink s2 = event -> {};
            var composite = new CompositeVerdictSink(List.of(s1, s2));

            assertThat(composite.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("dispatches event to all sinks in order")
        void dispatchesToAllSinksInOrder() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink first = event -> callOrder.add("first");
            VerdictSink second = event -> callOrder.add("second");
            VerdictSink third = event -> callOrder.add("third");

            var composite = new CompositeVerdictSink(List.of(first, second, third));
            composite.accept(sampleEvent());

            assertThat(callOrder).containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("passes the same event to each sink")
        void passesSameEventToEachSink() {
            List<VerdictEvent> received = new ArrayList<>();
            VerdictSink collector = received::add;

            var composite = new CompositeVerdictSink(List.of(collector, collector));
            var event = sampleEvent();
            composite.accept(event);

            assertThat(received).hasSize(2);
            assertThat(received).allSatisfy(e -> assertThat(e).isSameAs(event));
        }
    }

    @Nested
    @DisplayName("exception isolation")
    class ExceptionIsolation {

        @Test
        @DisplayName("continues dispatching when a sink throws")
        void continuesWhenSinkThrows() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink first = event -> callOrder.add("first");
            VerdictSink failing = event -> { throw new RuntimeException("boom"); };
            VerdictSink third = event -> callOrder.add("third");

            var composite = new CompositeVerdictSink(List.of(first, failing, third));
            composite.accept(sampleEvent());

            assertThat(callOrder).containsExactly("first", "third");
        }

        @Test
        @DisplayName("isolates exceptions from multiple failing sinks")
        void isolatesMultipleFailures() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink failing1 = event -> { throw new RuntimeException("fail-1"); };
            VerdictSink healthy = event -> callOrder.add("healthy");
            VerdictSink failing2 = event -> { throw new RuntimeException("fail-2"); };

            var composite = new CompositeVerdictSink(List.of(failing1, healthy, failing2));
            composite.accept(sampleEvent());

            assertThat(callOrder).containsExactly("healthy");
        }
    }
}
