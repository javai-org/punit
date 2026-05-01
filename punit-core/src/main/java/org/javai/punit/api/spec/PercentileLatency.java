package org.javai.punit.api.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.LatencySpec;

/**
 * A percentile-latency criterion. Reads {@link LatencyStatistics} from
 * the resolved baseline when in an empirical mode.
 *
 * <p>Three factory forms parallel {@code BernoulliPassRate}:
 * <ul>
 *   <li>{@link #meeting(LatencySpec, ThresholdOrigin)} — contractual;
 *       per-percentile ceilings come from the LatencySpec, origin is
 *       declared explicitly (non-empirical).</li>
 *   <li>{@link #empirical(PercentileKey, PercentileKey...)} — default
 *       closest-match resolution; thresholds for the asserted
 *       percentiles are derived from the baseline at evaluate
 *       time.</li>
 *   <li>{@link #empiricalFrom(Supplier, PercentileKey, PercentileKey...)}
 *       — pinned baseline.</li>
 * </ul>
 */
public final class PercentileLatency<OT> implements Criterion<OT, LatencyStatistics> {

    private static final String NAME = "percentile-latency";

    private enum Mode { CONTRACTUAL, EMPIRICAL_DEFAULT, EMPIRICAL_PINNED }

    private final Mode mode;
    private final LatencySpec declaredSpec;
    private final ThresholdOrigin origin;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final Supplier<Experiment> baselineSupplier;

    private PercentileLatency(
            Mode mode,
            LatencySpec declaredSpec,
            ThresholdOrigin origin,
            EnumSet<PercentileKey> assertedPercentiles,
            Supplier<Experiment> baselineSupplier) {
        this.mode = mode;
        this.declaredSpec = declaredSpec;
        this.origin = origin;
        this.assertedPercentiles = assertedPercentiles;
        this.baselineSupplier = baselineSupplier;
    }

    public static <OT> PercentileLatency<OT> meeting(LatencySpec spec, ThresholdOrigin origin) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(origin, "origin");
        if (!spec.hasAnyThreshold()) {
            throw new IllegalArgumentException(
                    "LatencySpec must assert at least one percentile — call .p50Millis(...), "
                            + ".p90Millis(...), .p95Millis(...), or .p99Millis(...) on the builder");
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "ThresholdOrigin.EMPIRICAL is reserved for the empirical factories; "
                            + "call PercentileLatency.empirical(...) or .empiricalFrom(...) instead");
        }
        return new PercentileLatency<>(
                Mode.CONTRACTUAL, spec, origin,
                assertedFromSpec(spec), null);
    }

    public static <OT> PercentileLatency<OT> empirical(PercentileKey first, PercentileKey... rest) {
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_DEFAULT, null, ThresholdOrigin.EMPIRICAL, asserted, null);
    }

    public static <OT> PercentileLatency<OT> empiricalFrom(
            Supplier<Experiment> baseline,
            PercentileKey first,
            PercentileKey... rest) {
        Objects.requireNonNull(baseline, "baseline");
        EnumSet<PercentileKey> asserted = toEnumSet(first, rest);
        return new PercentileLatency<>(
                Mode.EMPIRICAL_PINNED, null, ThresholdOrigin.EMPIRICAL, asserted, baseline);
    }

    /** The baseline supplier when pinned; empty otherwise. */
    public Optional<Supplier<Experiment>> baselineSupplier() {
        return Optional.ofNullable(baselineSupplier);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<LatencyStatistics> statisticsType() {
        return LatencyStatistics.class;
    }

    @Override
    public boolean isEmpirical() {
        return mode != Mode.CONTRACTUAL;
    }

    @Override
    public CriterionResult evaluate(EvaluationContext<OT, LatencyStatistics> ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SampleSummary<OT> summary = ctx.summary();
        if (summary.total() == 0) {
            return inconclusive("zero samples taken", Map.of());
        }

        Map<PercentileKey, Duration> thresholds;
        ThresholdOrigin resolvedOrigin;
        Integer baselineSampleCount = null;

        if (mode == Mode.CONTRACTUAL) {
            thresholds = thresholdsFromSpec(declaredSpec);
            resolvedOrigin = origin;
        } else {
            LatencyStatistics stats = ctx.baseline().orElse(null);
            if (stats == null) {
                return inconclusive(
                        "no matching baseline was resolvable for empirical thresholds; "
                                + "see DG02 §'Baseline relationship' for the resolution mechanism",
                        Map.of("assertedPercentiles", assertedCsv(), "origin", ThresholdOrigin.EMPIRICAL.name()));
            }
            Map<String, Object> empiricalDetail = Map.of("assertedPercentiles", assertedCsv());
            // Inputs-identity rule precedes the sample-size rule: an identity
            // mismatch is a more fundamental violation, so its diagnostic
            // wins when both would fire.
            String baselineIdentity = ctx.baselineInputsIdentity().orElseThrow(() ->
                    new IllegalStateException(
                            "baseline statistics were resolved but baselineInputsIdentity is empty — "
                                    + "the BaselineProvider is producing inconsistent state"));
            Optional<CriterionResult> identityViolation = EmpiricalChecks.inputsIdentityMatch(
                    NAME, ctx.testInputsIdentity(), baselineIdentity, empiricalDetail);
            if (identityViolation.isPresent()) {
                return identityViolation.get();
            }
            Optional<CriterionResult> sizeViolation = EmpiricalChecks.sampleSizeConstraint(
                    NAME, summary.total(), stats.sampleCount(), empiricalDetail);
            if (sizeViolation.isPresent()) {
                return sizeViolation.get();
            }
            thresholds = thresholdsFromBaseline(stats.percentiles());
            resolvedOrigin = ThresholdOrigin.EMPIRICAL;
            baselineSampleCount = stats.sampleCount();
        }

        LatencyResult observed = summary.latencyResult();
        List<PercentileKey> breachedKeys = new ArrayList<>();
        Map<PercentileKey, Duration> observedByKey = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : assertedPercentiles) {
            Duration obs = key.observed(observed);
            observedByKey.put(key, obs);
            Duration threshold = thresholds.get(key);
            if (threshold != null && obs.compareTo(threshold) > 0) {
                breachedKeys.add(key);
            }
        }

        Verdict verdict = breachedKeys.isEmpty() ? Verdict.PASS : Verdict.FAIL;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("assertedPercentiles", assertedCsv());
        detail.put("origin", resolvedOrigin.name());
        for (PercentileKey key : assertedPercentiles) {
            detail.put("observed." + key.detailKey(), observedByKey.get(key).toMillis());
            Duration t = thresholds.get(key);
            if (t != null) {
                detail.put("threshold." + key.detailKey(), t.toMillis());
            }
        }
        for (PercentileKey key : breachedKeys) {
            detail.put("breach." + key.detailKey(), observedByKey.get(key).toMillis());
        }
        if (mode != Mode.CONTRACTUAL) {
            detail.put("baselineSampleCount", baselineSampleCount);
        }

        String explanation = breachedKeys.isEmpty()
                ? String.format("all %d asserted percentiles met (origin=%s)",
                        assertedPercentiles.size(), resolvedOrigin)
                : String.format("%d of %d asserted percentiles breached (origin=%s): %s",
                        breachedKeys.size(), assertedPercentiles.size(), resolvedOrigin,
                        breachedKeys.stream().map(PercentileKey::detailKey)
                                .collect(Collectors.joining(", ")));

        return new CriterionResult(NAME, verdict, explanation, detail);
    }

    private CriterionResult inconclusive(String reason, Map<String, Object> detail) {
        return new CriterionResult(NAME, Verdict.INCONCLUSIVE, reason, detail);
    }

    private String assertedCsv() {
        return assertedPercentiles.stream().map(PercentileKey::detailKey)
                .collect(Collectors.joining(","));
    }

    private static EnumSet<PercentileKey> toEnumSet(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return set;
    }

    private static EnumSet<PercentileKey> assertedFromSpec(LatencySpec spec) {
        EnumSet<PercentileKey> set = EnumSet.noneOf(PercentileKey.class);
        for (PercentileKey key : PercentileKey.values()) {
            if (key.ceilingMillis(spec).isPresent()) {
                set.add(key);
            }
        }
        return set;
    }

    private static Map<PercentileKey, Duration> thresholdsFromSpec(LatencySpec spec) {
        Map<PercentileKey, Duration> map = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : PercentileKey.values()) {
            OptionalLong millis = key.ceilingMillis(spec);
            if (millis.isPresent()) {
                map.put(key, Duration.ofMillis(millis.getAsLong()));
            }
        }
        return map;
    }

    private Map<PercentileKey, Duration> thresholdsFromBaseline(LatencyResult percentiles) {
        Map<PercentileKey, Duration> map = new EnumMap<>(PercentileKey.class);
        for (PercentileKey key : assertedPercentiles) {
            map.put(key, key.observed(percentiles));
        }
        return map;
    }
}
