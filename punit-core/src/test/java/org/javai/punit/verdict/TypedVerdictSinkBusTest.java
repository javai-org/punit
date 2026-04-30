package org.javai.punit.verdict;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TypedVerdictSinkBus")
class TypedVerdictSinkBusTest {

    @BeforeEach
    void resetBus() {
        TypedVerdictSinkBus.reset();
    }

    @AfterEach
    void cleanUp() {
        TypedVerdictSinkBus.reset();
    }

    @Test
    @DisplayName("dispatch with no sinks is a no-op")
    void dispatchWithNoSinks() {
        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(TypedVerdictSinkBus.registeredCount()).isZero();
    }

    @Test
    @DisplayName("registered sink receives dispatched verdicts")
    void registeredSinkReceives() {
        List<ProbabilisticTestVerdict> received = new ArrayList<>();
        TypedVerdictSinkBus.register(received::add);

        ProbabilisticTestVerdict verdict = stubVerdict();
        TypedVerdictSinkBus.dispatch(verdict);

        assertThat(received).containsExactly(verdict);
    }

    @Test
    @DisplayName("multiple registered sinks all receive in registration order")
    void multipleSinksAllReceive() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        TypedVerdictSinkBus.register(v -> first.incrementAndGet());
        TypedVerdictSinkBus.register(v -> second.incrementAndGet());

        TypedVerdictSinkBus.dispatch(stubVerdict());
        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(first.get()).isEqualTo(2);
        assertThat(second.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("default sink supplier is materialised on first dispatch")
    void defaultSinkLazy() {
        AtomicInteger supplierCalled = new AtomicInteger();
        AtomicInteger sinkAccepts = new AtomicInteger();
        TypedVerdictSinkBus.installDefaultSink(() -> {
            supplierCalled.incrementAndGet();
            return v -> sinkAccepts.incrementAndGet();
        });

        // Supplier not yet called
        assertThat(supplierCalled.get()).isZero();

        TypedVerdictSinkBus.dispatch(stubVerdict());

        // Materialised on first dispatch
        assertThat(supplierCalled.get()).isEqualTo(1);
        assertThat(sinkAccepts.get()).isEqualTo(1);

        TypedVerdictSinkBus.dispatch(stubVerdict());

        // Not re-materialised
        assertThat(supplierCalled.get()).isEqualTo(1);
        assertThat(sinkAccepts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("installDefaultSink is idempotent — first install wins")
    void installDefaultSinkIdempotent() {
        AtomicInteger firstSupplier = new AtomicInteger();
        AtomicInteger secondSupplier = new AtomicInteger();
        TypedVerdictSinkBus.installDefaultSink(() -> {
            firstSupplier.incrementAndGet();
            return v -> { };
        });
        TypedVerdictSinkBus.installDefaultSink(() -> {
            secondSupplier.incrementAndGet();
            return v -> { };
        });

        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(firstSupplier.get()).isEqualTo(1);
        assertThat(secondSupplier.get()).isZero();
    }

    @Test
    @DisplayName("replaceAll suppresses default and installs only the supplied sinks")
    void replaceAllSuppressesDefault() {
        AtomicInteger defaultSink = new AtomicInteger();
        AtomicInteger replacement = new AtomicInteger();
        TypedVerdictSinkBus.installDefaultSink(() -> v -> defaultSink.incrementAndGet());
        TypedVerdictSinkBus.replaceAll(v -> replacement.incrementAndGet());

        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(defaultSink.get()).isZero();
        assertThat(replacement.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("replaceAll with empty array suppresses all dispatch")
    void replaceAllEmpty() {
        AtomicInteger anySink = new AtomicInteger();
        TypedVerdictSinkBus.installDefaultSink(() -> v -> anySink.incrementAndGet());
        TypedVerdictSinkBus.replaceAll();

        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(anySink.get()).isZero();
    }

    @Test
    @DisplayName("a throwing sink does not prevent other sinks from receiving")
    void throwingSinkIsolated() {
        AtomicInteger goodSink = new AtomicInteger();
        TypedVerdictSinkBus.register(v -> { throw new RuntimeException("boom"); });
        TypedVerdictSinkBus.register(v -> goodSink.incrementAndGet());

        TypedVerdictSinkBus.dispatch(stubVerdict());

        assertThat(goodSink.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("reset clears registered sinks and the default-installed flag")
    void resetClears() {
        AtomicInteger registered = new AtomicInteger();
        TypedVerdictSinkBus.register(v -> registered.incrementAndGet());
        assertThat(TypedVerdictSinkBus.registeredCount()).isEqualTo(1);

        TypedVerdictSinkBus.reset();

        assertThat(TypedVerdictSinkBus.registeredCount()).isZero();
        TypedVerdictSinkBus.dispatch(stubVerdict());
        assertThat(registered.get()).isZero();
    }

    private static ProbabilisticTestVerdict stubVerdict() {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-04-30T00:00:00Z"),
                new ProbabilisticTestVerdict.TestIdentity(
                        "com.example.MyTest", "stub", Optional.empty()),
                new ProbabilisticTestVerdict.ExecutionSummary(
                        10, 10, 10, 0, 0.9, 1.0, 100,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95,
                        UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.StatisticalAnalysis(
                        0.95, 0.0, 0.9, 1.0,
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of()),
                ProbabilisticTestVerdict.CovariateStatus.allAligned(),
                new ProbabilisticTestVerdict.CostSummary(
                        0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.Termination(
                        TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PUnitVerdict.PASS,
                "stub");
    }
}
