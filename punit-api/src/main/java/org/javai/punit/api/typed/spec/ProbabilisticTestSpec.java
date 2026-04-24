package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.DataGeneration;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;

/**
 * A probabilistic test: runs a {@link DataGeneration} and evaluates
 * one or more {@link Criterion} values against the resulting sample
 * summary.
 *
 * <p>Constructed via {@link #testing(DataGeneration)}. The sample
 * production is governed by the {@link DataGeneration}; the claims the
 * spec makes about the resulting population are expressed by the
 * criteria registered via {@link Builder#criterion(Criterion)}
 * (REQUIRED) and {@link Builder#reportOnly(Criterion)} (REPORT_ONLY).
 * The combined verdict is {@link Verdict#compose(List)}'d from the
 * REQUIRED subset.
 *
 * <p>Stage 3.5 ships the structural refactor; the framework's baseline
 * resolver is a stub that always returns empty, so empirical criteria
 * yield {@link Verdict#INCONCLUSIVE} with a diagnostic. The real
 * resolver and Wilson-score-aware comparisons land in Stage 4.
 */
public final class ProbabilisticTestSpec<FT, IT, OT> implements DataGenerationSpec<FT, IT, OT> {

    private final DataGeneration<FT, IT, OT> plan;
    private final List<Registered<OT>> registered;

    private SampleSummary<OT> summary;

    private ProbabilisticTestSpec(Builder<FT, IT, OT> b) {
        this.plan = b.plan;
        this.registered = List.copyOf(b.registered);
    }

    /** Entry point — compose a probabilistic test over a DataGeneration. */
    public static <FT, IT, OT> Builder<FT, IT, OT> testing(DataGeneration<FT, IT, OT> plan) {
        return new Builder<>(Objects.requireNonNull(plan, "plan"));
    }

    public DataGeneration<FT, IT, OT> dataGeneration() { return plan; }
    public int samples() { return plan.samples(); }

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return plan.useCaseFactory();
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return List.of(Configuration.<FT, IT, OT>of(plan.factors(), plan.inputs(), plan.samples()))
                .iterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        this.summary = summary;
    }

    @Override public EngineResult conclude() {
        SampleSummary<OT> s = summary != null
                ? summary
                : SampleSummary.from(List.of(), Duration.ZERO);
        FactorBundle factorBundle = FactorBundle.of(plan.factors());

        List<EvaluatedCriterion> evaluated = new ArrayList<>(registered.size());
        for (Registered<OT> entry : registered) {
            CriterionResult result = evaluate(entry.criterion(), s, factorBundle);
            evaluated.add(new EvaluatedCriterion(result, entry.role()));
        }

        Verdict composed = Verdict.compose(evaluated);
        List<String> warnings = new ArrayList<>();
        warnings.add("baseline resolver is a Stage-3.5 stub — empirical criteria "
                + "yield INCONCLUSIVE until Stage 4 lands real resolution");

        return new ProbabilisticTestResult(composed, factorBundle, evaluated, warnings);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CriterionResult evaluate(
            Criterion<OT, ?> criterion, SampleSummary<OT> s, FactorBundle factors) {
        // Stage 3.5 stub baseline resolver: always empty. The criterion dispatches
        // on its declared statisticsType() — the context's baseline is typed
        // Optional<S> where S matches the criterion's parameter.
        Criterion raw = criterion;
        EvaluationContext ctx = new EvaluationContext<OT, BaselineStatistics>() {
            @Override public SampleSummary<OT> summary() { return s; }
            @Override public Optional<BaselineStatistics> baseline() { return Optional.empty(); }
            @Override public FactorBundle factors() { return factors; }
        };
        return (CriterionResult) raw.evaluate(ctx);
    }

    // ── DataGenerationSpec pass-throughs to the plan ─────────────────

    @Override public Optional<Duration> timeBudget() { return plan.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return plan.tokenBudget(); }
    @Override public long tokenCharge() { return plan.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return plan.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return plan.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return plan.maxExampleFailures(); }
    @Override public LatencySpec latency() { return LatencySpec.disabled(); }

    // ── Builder ──────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private final DataGeneration<FT, IT, OT> plan;
        private final List<Registered<OT>> registered = new ArrayList<>();

        private Builder(DataGeneration<FT, IT, OT> plan) {
            this.plan = plan;
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

        public ProbabilisticTestSpec<FT, IT, OT> build() {
            return new ProbabilisticTestSpec<>(this);
        }
    }

    private record Registered<OT>(Criterion<OT, ?> criterion, CriterionRole role) {}
}
