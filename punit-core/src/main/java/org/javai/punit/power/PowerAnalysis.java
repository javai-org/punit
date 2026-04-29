package org.javai.punit.power;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.spec.Configuration;
import org.javai.punit.api.typed.spec.Experiment;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.api.typed.spec.Spec;
import org.javai.punit.api.typed.spec.TypedSpec;
import org.javai.punit.engine.baseline.BaselineResolver;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.statistics.SampleSizeCalculator;

/**
 * Authoring-time sample-size utility for the confidence-first
 * probabilistic-test pattern (PT03).
 *
 * <p>Given a baseline supplier and a desired minimum detectable
 * effect plus statistical power, computes the sample size at which
 * the one-proportion z-test (SC06) achieves that power against the
 * baseline rate.
 *
 * <p>Authors call this at spec-construction time and stamp the
 * computed sample count onto a template sampling, then bind factors
 * at the spec entry point:
 *
 * <pre>{@code
 * int n = PowerAnalysis.sampleSize(BASELINES, this::shoppingBaseline, 0.02, 0.80);
 * var sampling = samplingTemplate().samples(n);
 * return ProbabilisticTest.testing(sampling, factors)
 *         .criterion(BernoulliPassRate.empirical())
 *         .build();
 * }</pre>
 *
 * <p>The default confidence level is {@value #DEFAULT_CONFIDENCE} —
 * matching the empirical-criterion default.
 */
public final class PowerAnalysis {

    /** The default confidence (1 - α) used when none is specified. */
    public static final double DEFAULT_CONFIDENCE = 0.95;

    /** Criterion-name key the resolver looks under for the pass-rate baseline. */
    private static final String PASS_RATE_CRITERION = "bernoulli-pass-rate";

    /**
     * The actual sample-size formula lives in
     * {@link SampleSizeCalculator}, the dedicated home for statistical
     * calculations. This class is the bridge that resolves the
     * baseline rate; it does not duplicate the math.
     */
    private static final SampleSizeCalculator SAMPLE_SIZE_CALCULATOR = new SampleSizeCalculator();

    private PowerAnalysis() { }

    /**
     * Convenience overload that resolves the baseline directory the
     * same way the test pipeline does — via
     * {@link BaselineResolver#defaultDir()}, which honours the
     * {@code punit.baseline.dir} system property and falls back to
     * the project convention path.
     *
     * <p>This is the right call for tests using {@code PowerAnalysis}
     * inside an {@code @ProbabilisticTest} method body — the test
     * doesn't need to know where baselines live; the framework does.
     *
     * @see #sampleSize(Path, Supplier, double, double)
     */
    public static int sampleSize(
            Supplier<Experiment> baseline,
            double mde,
            double power) {
        return sampleSize(BaselineResolver.defaultDir(), baseline, mde, power);
    }

    /**
     * Computes the sample size required to detect a difference of at
     * least {@code mde} from the baseline's recorded pass rate at the
     * given statistical power, using the default confidence
     * ({@value #DEFAULT_CONFIDENCE}).
     *
     * <p>The supplier yields a {@code MEASURE}-flavour {@link Experiment};
     * the utility resolves the baseline file matching that experiment's
     * use-case identity and factors fingerprint under {@code baselineDir},
     * reads the recorded {@link PassRateStatistics}, and feeds the
     * recorded {@code observedPassRate} into the z-test sample-size
     * formula.
     *
     * @param baselineDir directory containing the baseline YAML files
     *                    the resolver searches
     * @param baseline    a supplier yielding the {@code MEASURE}
     *                    {@link Experiment} the sample size is being
     *                    planned against — typically a method
     *                    reference to an {@code @PunitExperiment} method
     * @param mde         the minimum detectable effect, in (0, 1)
     * @param power       the required statistical power, in (0, 1)
     * @return the required sample count, ceiling-rounded
     * @throws NullPointerException     if any argument is null, or the
     *                                  supplier returns null
     * @throws IllegalArgumentException if {@code mde} ∉ (0, 1) or
     *                                  {@code power} ∉ (0, 1) or the
     *                                  supplier yields a non-MEASURE
     *                                  experiment, or the resolved
     *                                  baseline rate is incompatible
     *                                  with the requested MDE
     *                                  ({@code rate ± mde} outside (0, 1))
     * @throws IllegalStateException    if no matching baseline file is
     *                                  resolvable under {@code baselineDir}
     */
    public static int sampleSize(
            Path baselineDir,
            Supplier<Experiment> baseline,
            double mde,
            double power) {
        Objects.requireNonNull(baselineDir, "baselineDir");
        Objects.requireNonNull(baseline, "baseline");
        validate(mde, power);

        Experiment experiment = Objects.requireNonNull(
                baseline.get(), "baseline supplier returned null");
        if (experiment.kind() != Experiment.Kind.MEASURE) {
            throw new IllegalArgumentException(
                    "PowerAnalysis.sampleSize requires a MEASURE-flavour Experiment "
                            + "(one that produces a baseline); got " + experiment.kind()
                            + ". Pass a method reference to an @PunitExperiment method "
                            + "whose body returns Experiment.measuring(...).build().");
        }

        BaselineLookup lookup = experiment.dispatch(LOOKUP_DISPATCHER);
        BaselineResolver resolver = new BaselineResolver(baselineDir);
        PassRateStatistics stats = resolver.resolve(
                lookup.useCaseId(),
                lookup.factorsFingerprint(),
                PASS_RATE_CRITERION,
                PassRateStatistics.class)
                .orElseThrow(() -> new IllegalStateException(
                        "no baseline found for use case '" + lookup.useCaseId()
                                + "' (factors fingerprint " + lookup.factorsFingerprint()
                                + ") under " + baselineDir
                                + " — run the baseline measure before planning a test against it."));

        double observedRate = stats.observedPassRate();
        if (observedRate - mde <= 0.0 || observedRate + mde >= 1.0) {
            throw new IllegalArgumentException(
                    "baseline observed rate " + observedRate
                            + " is incompatible with mde=" + mde
                            + " — the [rate-mde, rate+mde] interval must lie in (0, 1)");
        }

        // Sample-size formula (SC06) lives in SampleSizeCalculator — the
        // dedicated statistics-package home. This bridge resolves the
        // baseline rate and delegates the math.
        return SAMPLE_SIZE_CALCULATOR
                .calculateForPower(observedRate, mde, DEFAULT_CONFIDENCE, power)
                .requiredSamples();
    }

    private static void validate(double mde, double power) {
        if (Double.isNaN(mde) || mde <= 0.0 || mde >= 1.0) {
            throw new IllegalArgumentException(
                    "mde must be in (0, 1), got " + mde);
        }
        if (Double.isNaN(power) || power <= 0.0 || power >= 1.0) {
            throw new IllegalArgumentException(
                    "power must be in (0, 1), got " + power);
        }
    }

    /** Carrier for the two identity values the resolver needs. */
    private record BaselineLookup(String useCaseId, String factorsFingerprint) { }

    /**
     * Dispatcher that captures the {@code <FT>} type parameter from the
     * spec's typed view long enough to call
     * {@code useCaseFactory.apply(factors).id()} and compute the
     * factors fingerprint, then collapses the result back to a
     * non-generic carrier.
     */
    private static final Spec.Dispatcher<BaselineLookup> LOOKUP_DISPATCHER =
            new Spec.Dispatcher<>() {
                @Override
                public <FT, IT, OT> BaselineLookup apply(TypedSpec<FT, IT, OT> typed) {
                    Iterator<Configuration<FT, IT, OT>> iterator = typed.configurations();
                    if (!iterator.hasNext()) {
                        throw new IllegalStateException(
                                "MEASURE experiment produced no configurations — "
                                        + "the typed spec is malformed");
                    }
                    Configuration<FT, IT, OT> cfg = iterator.next();
                    FT factors = cfg.factors();
                    UseCase<FT, IT, OT> useCase = typed.useCaseFactory().apply(factors);
                    String useCaseId = useCase.id();
                    String fingerprint = FactorsFingerprint.of(FactorBundle.of(factors));
                    return new BaselineLookup(useCaseId, fingerprint);
                }
            };
}
