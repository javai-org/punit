package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test: runs a {@link Sampling} bound to a factor
 * bundle and evaluates one or more {@link Criterion} values against
 * the resulting sample summary.
 *
 * <p>Constructed via {@link #testing(Sampling, Object)}. The sample
 * production is governed by the factor-free {@link Sampling}; the
 * second argument is the factor bundle the test runs against. The
 * claims the spec makes about the resulting population are expressed
 * by the criteria registered via {@link Builder#criterion(Criterion)}
 * (REQUIRED) and {@link Builder#reportOnly(Criterion)} (REPORT_ONLY).
 * The combined verdict is {@link Verdict#compose(List)}'d from the
 * REQUIRED subset.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed {@link Builder}, and the engine
 * recovers the typed view via {@link Spec#dispatch(Dispatcher)}.
 */
public final class ProbabilisticTestSpec implements Spec {

    private final Internal<?, ?, ?> internal;

    private ProbabilisticTestSpec(Internal<?, ?, ?> internal) {
        this.internal = internal;
    }

    /**
     * Entry point — compose a probabilistic test over a factor-free
     * {@link Sampling} and the factor bundle it should run against.
     */
    public static <FT, IT, OT> Builder<FT, IT, OT> testing(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new Builder<>(
                Objects.requireNonNull(sampling, "sampling"),
                Objects.requireNonNull(factors, "factors"));
    }

    /** Sample count for this test, taken from its sampling. */
    public int samples() { return internal.sampling.samples(); }

    /**
     * The test's declared intent. {@link TestIntent#VERIFICATION} (default)
     * claims evidential status; {@link TestIntent#SMOKE} is a
     * sentinel-grade lightweight check.
     */
    public TestIntent intent() { return internal.intent; }

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return doDispatch(internal, dispatcher);
    }

    private static <FT, IT, OT, R> R doDispatch(Internal<FT, IT, OT> typed, Dispatcher<R> d) {
        return d.apply(typed);
    }

    // ── Typed internal delegate (engine-facing) ─────────────────────

    private static final class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered;
        private final TestIntent intent;

        private SampleSummary<OT> summary;

        private Internal(Builder<FT, IT, OT> b) {
            this.sampling = b.sampling;
            this.factors = b.factors;
            this.registered = List.copyOf(b.registered);
            this.intent = b.intent;
        }

        @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
            return sampling.useCaseFactory();
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return List.of(Configuration.<FT, IT, OT>of(factors, sampling.inputs(), sampling.samples()))
                    .iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            this.summary = summary;
        }

        @Override public EngineResult conclude() {
            SampleSummary<OT> s = summary != null
                    ? summary
                    : SampleSummary.from(List.of(), Duration.ZERO);
            FactorBundle factorBundle = FactorBundle.of(factors);

            List<EvaluatedCriterion> evaluated = new ArrayList<>(registered.size());
            for (Registered<OT> entry : registered) {
                CriterionResult result = evaluate(entry.criterion(), s, factorBundle);
                evaluated.add(new EvaluatedCriterion(result, entry.role()));
            }

            Verdict composed = Verdict.compose(evaluated);
            List<String> warnings = new ArrayList<>();
            warnings.add("baseline resolver is a Stage-3.5 stub — empirical criteria "
                    + "yield INCONCLUSIVE until Stage 4 lands real resolution");

            return new ProbabilisticTestResult(composed, factorBundle, evaluated, intent, warnings);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CriterionResult evaluate(
                Criterion<OT, ?> criterion, SampleSummary<OT> s, FactorBundle factorBundle) {
            Criterion raw = criterion;
            EvaluationContext ctx = new EvaluationContext<OT, BaselineStatistics>() {
                @Override public SampleSummary<OT> summary() { return s; }
                @Override public Optional<BaselineStatistics> baseline() { return Optional.empty(); }
                @Override public FactorBundle factors() { return factorBundle; }
            };
            return (CriterionResult) raw.evaluate(ctx);
        }

        @Override public Optional<Duration> timeBudget() { return sampling.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return sampling.tokenBudget(); }
        @Override public long tokenCharge() { return sampling.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return sampling.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return sampling.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return sampling.maxExampleFailures(); }
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Registered<OT>> registered = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;

        private Builder(Sampling<FT, IT, OT> sampling, FT factors) {
            this.sampling = sampling;
            this.factors = factors;
        }

        /** Register a criterion that contributes to the combined verdict. */
        public Builder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REQUIRED));
            return this;
        }

        /** Register a criterion whose result is attached but excluded from composition. */
        public Builder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            registered.add(new Registered<>(criterion, CriterionRole.REPORT_ONLY));
            return this;
        }

        /**
         * Declares the test's intent. Defaults to
         * {@link TestIntent#VERIFICATION}. Authors of sentinel-grade
         * smoke checks against external providers opt into
         * {@link TestIntent#SMOKE} explicitly.
         */
        public Builder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTestSpec build() {
            return new ProbabilisticTestSpec(new Internal<>(this));
        }
    }

    private record Registered<OT>(Criterion<OT, ?> criterion, CriterionRole role) {}
}
