package org.javai.punit.internal.engine.baseline;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.Configuration;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.Spec;
import org.javai.punit.api.spec.TypedSpec;
import org.javai.punit.internal.engine.covariate.CovariateResolver;
import org.javai.punit.statistics.SampleSizeCalculator;

/**
 * Authoring-time sample-size utility for the confidence-first
 * probabilistic-test pattern.
 *
 * <p>Given a baseline supplier and a desired minimum detectable
 * effect plus statistical power, computes the sample size at which
 * the one-proportion z-test achieves that power against the baseline
 * rate.
 *
 * <p>Authors call this at spec-construction time and stamp the
 * computed sample count onto a template sampling, then bind factors
 * at the spec entry point:
 *
 * <pre>{@code
 * int n = PowerAnalysis.sampleSize(BASELINES, this::shoppingBaseline, 0.02, 0.80);
 * var sampling = samplingTemplate().samples(n);
 * return ProbabilisticTest.testing(sampling, factors)
 *         .criterion(PassRate.empirical())
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
     *                    reference to an {@code @PUnitExperiment} method
     * @param mde         the minimum detectable effect, in (0, 1)
     * @param power       the required statistical power, in (0, 1)
     * @return the required sample count, ceiling-rounded
     * @throws NullPointerException     if any argument is null, or the
     *                                  supplier returns null
     * @throws IllegalArgumentException if {@code mde} ∉ (0, 1) or
     *                                  {@code power} ∉ (0, 1) or the
     *                                  supplier yields a non-MEASURE
     *                                  experiment, or the alternative-
     *                                  hypothesis rate {@code rate − mde}
     *                                  is not strictly positive
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
                            + ". Pass a method reference to an @PUnitExperiment method "
                            + "whose body returns Experiment.measuring(...).build().");
        }

        BaselineLookup lookup = experiment.dispatch(LOOKUP_DISPATCHER);
        BaselineResolver resolver = new BaselineResolver(baselineDir);
        PassRateStatistics stats = resolver.resolve(
                lookup.useCaseId(),
                lookup.factorsFingerprint(),
                PASS_RATE_CRITERION,
                PassRateStatistics.class,
                lookup.profile(),
                lookup.declarations())
                .orElseThrow(() -> new IllegalStateException(
                        "no baseline found for use case '" + lookup.useCaseId()
                                + "' (factors fingerprint " + lookup.factorsFingerprint()
                                + (lookup.profile().isEmpty() ? ""
                                        : ", covariates " + formatProfile(lookup.profile()))
                                + ") under " + baselineDir
                                + " — run the baseline measure before planning a test against it."));

        double observedRate = stats.observedPassRate();
        // One-sided check: degradation testing only cares about the
        // lower side, so the alternative-hypothesis rate p1 = rate − mde
        // must be strictly positive. The upper side (rate + mde) is
        // never used by the formula and intentionally not bounded —
        // perfect baselines (rate = 1.0) are valid input here, with
        // σ0 = 0 collapsing the formula to n = (z_β · σ1)² / δ²
        // inside SampleSizeCalculator.
        if (observedRate - mde <= 0.0) {
            throw new IllegalArgumentException(
                    "baseline observed rate " + observedRate
                            + " is incompatible with mde=" + mde
                            + " — the alternative-hypothesis rate (rate − mde) "
                            + "must be > 0 for one-sided degradation detection.");
        }

        // The sample-size formula lives in SampleSizeCalculator — the
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

    /**
     * Carrier for the identity values and covariate context the
     * resolver needs. The covariate fields are required so the
     * authoring-time resolution path matches the JUnit-extension test
     * pipeline: a covariate-stamped baseline only matches when the
     * caller supplies the same profile + declarations the test would
     * supply at run time.
     */
    private record BaselineLookup(
            String useCaseId,
            String factorsFingerprint,
            CovariateProfile profile,
            List<Covariate> declarations) { }

    /**
     * Dispatcher that captures the {@code <FT>} type parameter from the
     * spec's engine-facing view long enough to call
     * {@code useCaseFactory.apply(factors).id()}, compute the factors
     * fingerprint, and resolve the use case's covariate profile, then
     * collapses the result back to a non-generic carrier.
     */
    private static final Spec.Dispatcher<BaselineLookup> LOOKUP_DISPATCHER =
            new Spec.Dispatcher<>() {
                @Override
                public <FT, IT, OT> BaselineLookup apply(TypedSpec<FT, IT, OT> spec) {
                    Iterator<Configuration<FT, IT, OT>> iterator = spec.configurations();
                    if (!iterator.hasNext()) {
                        throw new IllegalStateException(
                                "MEASURE experiment produced no configurations — "
                                        + "the spec is malformed");
                    }
                    Configuration<FT, IT, OT> cfg = iterator.next();
                    FT factors = cfg.factors();
                    UseCase<FT, IT, OT> useCase = spec.useCaseFactory().apply(factors);
                    String useCaseId = useCase.id();
                    String fingerprint = FactorsFingerprint.of(FactorBundle.of(factors));
                    List<Covariate> declarations = useCase.covariates();
                    CovariateProfile profile = declarations.isEmpty()
                            ? CovariateProfile.empty()
                            : CovariateResolver.defaults().resolve(
                                    declarations, useCase.customCovariateResolvers());
                    return new BaselineLookup(useCaseId, fingerprint, profile, declarations);
                }
            };

    private static String formatProfile(CovariateProfile profile) {
        Map<String, String> values = profile.values();
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
