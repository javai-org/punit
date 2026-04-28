package org.javai.punit.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.Criterion;
import org.javai.punit.api.typed.spec.CriterionResult;
import org.javai.punit.api.typed.spec.EngineResult;
import org.javai.punit.api.typed.spec.EvaluatedCriterion;
import org.javai.punit.api.typed.spec.Experiment;
import org.javai.punit.api.typed.spec.FactorsStepper;
import org.javai.punit.api.typed.spec.ProbabilisticTest;
import org.javai.punit.api.typed.spec.ProbabilisticTestResult;
import org.javai.punit.api.typed.spec.Scorer;
import org.javai.punit.api.typed.spec.Verdict;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;
import org.javai.punit.engine.Engine;
import org.javai.punit.engine.baseline.ProfileBoundBaselineProvider;
import org.javai.punit.engine.covariate.CovariateResolver;
import org.javai.punit.engine.criteria.Feasibility;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The user-facing entry point for the typed-compositional API under
 * JUnit. Authors call {@link #testing}, {@link #measuring},
 * {@link #exploring}, and {@link #optimizing} from within their
 * {@link org.javai.punit.api.ProbabilisticTest @ProbabilisticTest}
 * and {@link org.javai.punit.api.Experiment @Experiment} methods.
 *
 * <p>Each factory returns a builder that wraps the corresponding
 * {@link org.javai.punit.api.typed.spec.Experiment}- or
 * {@link org.javai.punit.api.typed.spec.ProbabilisticTest}-builder
 * in {@code punit-api}, adding a terminal {@link MeasureBuilder#run}
 * (for experiments) or {@link TestBuilder#assertPasses} (for
 * probabilistic tests). Each terminal drives the spec through
 * {@link Engine} and translates the outcome:
 *
 * <ul>
 *   <li>Experiments produce artefacts (baseline files, exploration
 *       grids, optimization histories). {@code .run()} returns
 *       normally on success; engine-level defects propagate as
 *       runtime exceptions.</li>
 *   <li>Probabilistic tests produce verdicts. {@code .assertPasses()}
 *       returns normally on {@link Verdict#PASS}, throws
 *       {@link AssertionFailedError} on {@link Verdict#FAIL}, and
 *       throws {@link TestAbortedException} on
 *       {@link Verdict#INCONCLUSIVE} (configuration / environment
 *       drift, not service degradation).</li>
 * </ul>
 *
 * <p>A {@code .build()} terminal is also available on every builder
 * — used for the
 * {@link org.javai.punit.api.typed.spec.BernoulliPassRate#empiricalFrom
 * empiricalFrom(supplier)} pattern, where a method returning a built
 * {@link Experiment} value supplies the baseline a probabilistic
 * test compares against.
 */
public final class Punit {

    private Punit() { }

    // ── Sampling-bound factories ────────────────────────────────────

    /** Compose a contractual probabilistic test against a shared sampling. */
    public static <FT, IT, OT> TestBuilder<FT, IT, OT> testing(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new TestBuilder<>(
                ProbabilisticTest.testing(sampling, factors),
                sampling, factors);
    }

    /**
     * Compose an empirical probabilistic test that derives its sampling
     * and factors from a baseline {@link Experiment} supplier. The
     * author specifies only the (typically smaller) sample count and
     * the criterion; identity, factors, and inputs follow from the
     * baseline.
     */
    public static EmpiricalTestBuilder testing(Supplier<Experiment> baselineSupplier) {
        return new EmpiricalTestBuilder(Objects.requireNonNull(baselineSupplier, "baselineSupplier"));
    }

    /** Compose a measure experiment over a sampling and bound factors. */
    public static <FT, IT, OT> MeasureBuilder<FT, IT, OT> measuring(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new MeasureBuilder<>(Experiment.measuring(sampling, factors));
    }

    /** Compose an explore experiment over a sampling; supply the grid via the builder. */
    public static <FT, IT, OT> ExploreBuilder<FT, IT, OT> exploring(
            Sampling<FT, IT, OT> sampling) {
        return new ExploreBuilder<>(Experiment.exploring(sampling));
    }

    /**
     * Compose an optimize experiment over a sampling; supply initial
     * factors, the stepper, and the scorer/direction via the builder.
     */
    public static <FT, IT, OT> OptimizeBuilder<FT, IT, OT> optimizing(
            Sampling<FT, IT, OT> sampling) {
        return new OptimizeBuilder<>(Experiment.optimizing(sampling));
    }

    // ── Internal: drive an experiment / test ────────────────────────

    private static EngineResult drive(org.javai.punit.api.typed.spec.Spec spec) {
        BaselineProvider provider = profileBoundProvider(spec);
        return new Engine(provider).run(spec);
    }

    private static void driveAndEmit(Experiment experiment) {
        drive(experiment);
        BaselineEmitter.emit(experiment, BaselineProviderResolver.resolveDir());
    }

    /**
     * Resolves the use case's covariate profile from {@code spec}'s
     * first configuration and wraps {@link BaselineProviderResolver}'s
     * provider so the engine and pre-flight feasibility see a
     * profile-bound view.
     *
     * <p>Per UC04 the profile is resolved once per run, before any
     * samples execute. {@link ProfileBoundBaselineProvider#bind}
     * returns the wrapped delegate unchanged when the use case
     * declared no covariates, so non-covariate tests pay no cost
     * here.
     */
    private static BaselineProvider profileBoundProvider(
            org.javai.punit.api.typed.spec.Spec spec) {
        BaselineProvider provider = BaselineProviderResolver.resolve();
        return spec.dispatch(new org.javai.punit.api.typed.spec.Spec.Dispatcher<>() {
            @Override
            public <FT, IT, OT> BaselineProvider apply(
                    org.javai.punit.api.typed.spec.TypedSpec<FT, IT, OT> typed) {
                var configs = typed.configurations();
                if (!configs.hasNext()) {
                    return provider;
                }
                FT factors = configs.next().factors();
                UseCase<FT, IT, OT> useCase = typed.useCaseFactory().apply(factors);
                List<Covariate> declarations = useCase.covariates();
                if (declarations.isEmpty()) {
                    return provider;
                }
                CovariateProfile profile = CovariateResolver.defaults()
                        .resolve(declarations, useCase.customCovariateResolvers());
                return ProfileBoundBaselineProvider.bind(provider, profile, declarations);
            }
        });
    }

    private static void translate(ProbabilisticTestResult result) {
        Verdict verdict = result.verdict();
        if (verdict == Verdict.PASS) {
            return;
        }
        String message = formatMessage(result);
        if (verdict == Verdict.INCONCLUSIVE) {
            throw new TestAbortedException(message);
        }
        throw new AssertionFailedError(message);
    }

    private static String formatMessage(ProbabilisticTestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.verdict());
        List<EvaluatedCriterion> evaluated = result.criterionResults();
        if (!evaluated.isEmpty()) {
            sb.append('\n');
            for (EvaluatedCriterion entry : evaluated) {
                CriterionResult cr = entry.result();
                sb.append("  [").append(entry.role()).append("] ")
                        .append(cr.criterionName()).append(" → ")
                        .append(cr.verdict()).append(": ")
                        .append(cr.explanation()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    // ── Wrapper builders ────────────────────────────────────────────

    /** Wrapper around {@link Experiment.MeasureBuilder} adding {@link #run}. */
    public static final class MeasureBuilder<FT, IT, OT> {
        private final Experiment.MeasureBuilder<FT, IT, OT> delegate;

        MeasureBuilder(Experiment.MeasureBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public MeasureBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public MeasureBuilder<FT, IT, OT> expectedOutputs(List<OT> outputs) {
            delegate.expectedOutputs(outputs);
            return this;
        }

        @SafeVarargs
        public final MeasureBuilder<FT, IT, OT> expectedOutputs(OT... outputs) {
            delegate.expectedOutputs(outputs);
            return this;
        }

        public MeasureBuilder<FT, IT, OT> matcher(ValueMatcher<OT> matcher) {
            delegate.matcher(matcher);
            return this;
        }

        public MeasureBuilder<FT, IT, OT> expiresInDays(int days) {
            delegate.expiresInDays(days);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        public void run() {
            // Measure runs and emits a baseline file when a baseline directory
            // is configured (system property or project convention). The
            // emission is silent when no directory resolves — the run itself
            // succeeded; the artefact just has nowhere to land.
            driveAndEmit(build());
        }
    }

    /** Wrapper around {@link Experiment.ExploreBuilder} adding {@link #run}. */
    public static final class ExploreBuilder<FT, IT, OT> {
        private final Experiment.ExploreBuilder<FT, IT, OT> delegate;

        ExploreBuilder(Experiment.ExploreBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public ExploreBuilder<FT, IT, OT> grid(List<FT> grid) {
            delegate.grid(grid);
            return this;
        }

        @SafeVarargs
        public final ExploreBuilder<FT, IT, OT> grid(FT... grid) {
            delegate.grid(grid);
            return this;
        }

        public ExploreBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        public void run() {
            drive(build());
        }
    }

    /** Wrapper around {@link Experiment.OptimizeBuilder} adding {@link #run}. */
    public static final class OptimizeBuilder<FT, IT, OT> {
        private final Experiment.OptimizeBuilder<FT, IT, OT> delegate;

        OptimizeBuilder(Experiment.OptimizeBuilder<FT, IT, OT> delegate) {
            this.delegate = delegate;
        }

        public OptimizeBuilder<FT, IT, OT> initialFactors(FT factors) {
            delegate.initialFactors(factors);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> stepper(FactorsStepper<FT> s) {
            delegate.stepper(s);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> maximize(Scorer scorer) {
            delegate.maximize(scorer);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> minimize(Scorer scorer) {
            delegate.minimize(scorer);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> maxIterations(int n) {
            delegate.maxIterations(n);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> noImprovementWindow(int n) {
            delegate.noImprovementWindow(n);
            return this;
        }

        public OptimizeBuilder<FT, IT, OT> experimentId(String id) {
            delegate.experimentId(id);
            return this;
        }

        public Experiment build() {
            return delegate.build();
        }

        public void run() {
            drive(build());
        }
    }

    /** Wrapper around {@link ProbabilisticTest.Builder} adding {@link #assertPasses}. */
    public static final class TestBuilder<FT, IT, OT> {
        private final ProbabilisticTest.Builder<FT, IT, OT> delegate;
        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private final List<Criterion<OT, ?>> requiredCriteria = new ArrayList<>();
        private TestIntent intent = TestIntent.VERIFICATION;

        TestBuilder(ProbabilisticTest.Builder<FT, IT, OT> delegate,
                    Sampling<FT, IT, OT> sampling, FT factors) {
            this.delegate = delegate;
            this.sampling = sampling;
            this.factors = factors;
        }

        public TestBuilder<FT, IT, OT> criterion(Criterion<OT, ?> criterion) {
            delegate.criterion(criterion);
            requiredCriteria.add(criterion);
            return this;
        }

        public TestBuilder<FT, IT, OT> reportOnly(Criterion<OT, ?> criterion) {
            delegate.reportOnly(criterion);
            // REPORT_ONLY criteria don't gate the verdict — feasibility doesn't
            // apply, so we don't capture them here.
            return this;
        }

        public TestBuilder<FT, IT, OT> intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            delegate.intent(intent);
            return this;
        }

        public ProbabilisticTest build() {
            return delegate.build();
        }

        public void assertPasses() {
            ProbabilisticTest spec = build();
            List<String> warnings = preflightFeasibility();
            EngineResult result = drive(spec);
            if (!(result instanceof ProbabilisticTestResult typed)) {
                throw new IllegalStateException(
                        "Engine produced unexpected result type: " + result.getClass().getName());
            }
            warnings.forEach(System.err::println);
            translate(typed);
        }

        private List<String> preflightFeasibility() {
            UseCase<FT, IT, OT> useCase = sampling.useCaseFactory().apply(factors);
            String useCaseId = useCase.id();
            FactorBundle bundle = FactorBundle.of(factors);
            BaselineProvider raw = BaselineProviderResolver.resolve();
            // Bind the run's covariate profile so Feasibility sees the
            // baseline that will actually drive evaluation, not a sibling
            // covariate-tagged file that happens to share useCaseId+ff.
            List<Covariate> declarations = useCase.covariates();
            CovariateProfile profile = declarations.isEmpty()
                    ? CovariateProfile.empty()
                    : CovariateResolver.defaults()
                            .resolve(declarations, useCase.customCovariateResolvers());
            BaselineProvider provider = ProfileBoundBaselineProvider.bind(
                    raw, profile, declarations);
            List<String> warnings = new ArrayList<>();
            for (Criterion<OT, ?> c : requiredCriteria) {
                warnings.addAll(Feasibility.check(
                        sampling.samples(), c, useCaseId, bundle, intent, provider));
            }
            return warnings;
        }
    }

    /**
     * Builder for the empirical probabilistic-test entry point —
     * {@link Punit#testing(Supplier)}. Pulls sampling and factors
     * from the baseline supplier; the author specifies only the
     * sample count and criterion.
     */
    public static final class EmpiricalTestBuilder {
        private final Supplier<Experiment> baselineSupplier;
        private Integer samples;
        private Criterion<?, ?> criterion;
        private TestIntent intent = TestIntent.VERIFICATION;

        EmpiricalTestBuilder(Supplier<Experiment> baselineSupplier) {
            this.baselineSupplier = baselineSupplier;
        }

        public EmpiricalTestBuilder samples(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("samples must be >= 1, got " + n);
            }
            this.samples = n;
            return this;
        }

        public EmpiricalTestBuilder criterion(Criterion<?, ?> criterion) {
            Objects.requireNonNull(criterion, "criterion");
            if (!criterion.isEmpirical()) {
                throw new IllegalArgumentException(
                        "Punit.testing(supplier) only accepts empirical criteria; "
                                + "got contractual " + criterion.name()
                                + ". Use Punit.testing(sampling, factors) for contractual tests.");
            }
            this.criterion = criterion;
            return this;
        }

        public EmpiricalTestBuilder intent(TestIntent intent) {
            this.intent = Objects.requireNonNull(intent, "intent");
            return this;
        }

        public ProbabilisticTest build() {
            if (samples == null) {
                throw new IllegalStateException(
                        "samples is required — call .samples(n) before .build() / .assertPasses()");
            }
            if (criterion == null) {
                throw new IllegalStateException(
                        "criterion is required — call .criterion(BernoulliPassRate.empirical()) "
                                + "or similar before .build() / .assertPasses()");
            }
            Experiment baseline = Objects.requireNonNull(
                    baselineSupplier.get(),
                    "baseline supplier returned null");
            return EmpiricalTestComposer.compose(baseline, samples, criterion, intent);
        }

        public void assertPasses() {
            ProbabilisticTest spec = build();
            List<String> warnings = preflightFeasibility(spec);
            EngineResult result = drive(spec);
            if (!(result instanceof ProbabilisticTestResult typed)) {
                throw new IllegalStateException(
                        "Engine produced unexpected result type: " + result.getClass().getName());
            }
            warnings.forEach(System.err::println);
            translate(typed);
        }

        @SuppressWarnings("unchecked")
        private List<String> preflightFeasibility(ProbabilisticTest spec) {
            BaselineProvider raw = BaselineProviderResolver.resolve();
            List<String> warnings = new ArrayList<>();
            spec.dispatch(new org.javai.punit.api.typed.spec.Spec.Dispatcher<Void>() {
                @Override
                public <FT, IT, OT> Void apply(
                        org.javai.punit.api.typed.spec.TypedSpec<FT, IT, OT> typed) {
                    var cfg = typed.configurations().next();
                    FT factors = cfg.factors();
                    UseCase<FT, IT, OT> useCase = typed.useCaseFactory().apply(factors);
                    String useCaseId = useCase.id();
                    FactorBundle bundle = FactorBundle.of(factors);
                    List<Covariate> declarations = useCase.covariates();
                    CovariateProfile profile = declarations.isEmpty()
                            ? CovariateProfile.empty()
                            : CovariateResolver.defaults()
                                    .resolve(declarations, useCase.customCovariateResolvers());
                    BaselineProvider provider = ProfileBoundBaselineProvider.bind(
                            raw, profile, declarations);
                    warnings.addAll(Feasibility.check(
                            cfg.samples(),
                            (Criterion<Object, ?>) criterion,
                            useCaseId, bundle, intent, provider));
                    return null;
                }
            });
            return warnings;
        }
    }
}
