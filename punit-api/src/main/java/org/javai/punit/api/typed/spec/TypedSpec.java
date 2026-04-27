package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;

/**
 * Engine-facing strategy contract carrying the typed methods the
 * engine programs against. Authors do not implement this interface;
 * it is reached only via {@link Spec#dispatch(Spec.Dispatcher)} and
 * exists so the engine can stay fully type-safe while the public
 * spec types ({@link ProbabilisticTest} et al.) carry no
 * type parameters.
 *
 * <p>Each public spec class holds an internal instance of this
 * interface and exposes it to the engine through the dispatch
 * pattern, capturing {@code <FT, IT, OT>} from a wildcard reference
 * into a generic method invocation.
 *
 * @param <FT> the factor record type
 * @param <IT> the per-sample input type
 * @param <OT> the per-sample outcome value type
 */
public interface TypedSpec<FT, IT, OT> {

    Iterator<Configuration<FT, IT, OT>> configurations();

    Function<FT, UseCase<FT, IT, OT>> useCaseFactory();

    default Optional<ValueMatcher<OT>> matcher() {
        return Optional.empty();
    }

    void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary);

    /**
     * Engine-driven termination of the run. The {@code provider}
     * supplies baseline statistics for empirical criteria; specs that
     * make no empirical claims (every {@link Experiment} kind) ignore
     * the parameter.
     */
    EngineResult conclude(BaselineProvider provider);

    /** Convenience shortcut for tests and other callers without a real provider. */
    default EngineResult conclude() {
        return conclude(BaselineProvider.EMPTY);
    }

    default Optional<Duration> timeBudget() { return Optional.empty(); }
    default OptionalLong tokenBudget() { return OptionalLong.empty(); }
    default long tokenCharge() { return 0L; }
    default BudgetExhaustionPolicy budgetPolicy() { return BudgetExhaustionPolicy.FAIL; }
    default ExceptionPolicy exceptionPolicy() { return ExceptionPolicy.ABORT_TEST; }
    default int maxExampleFailures() { return 10; }
}
