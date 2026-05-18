package org.javai.punit.internal.engine.criteria;

import java.util.Optional;

import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.criterion.CriterionPosture;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.PercentileLatency;
import org.javai.punit.api.spec.SpecCriterionDeriver;

/**
 * The framework's default {@link SpecCriterionDeriver}. Maps
 * statistical-pass-rate postures to {@link PassRate} variants, and
 * latency postures to {@link PercentileLatency} variants.
 *
 * <p>Registered via {@code META-INF/services} so the api-side builder
 * finds it through {@link java.util.ServiceLoader} without importing
 * the {@code internal} namespace.
 */
public final class PostureBasedSpecCriterionDeriver implements SpecCriterionDeriver {

    @Override
    @SuppressWarnings("unchecked")
    public <O> Optional<Criterion<O, ?>> derive(CriterionPosture posture) {
        if (posture.isLatency()) {
            return Optional.of((Criterion<O, ?>) deriveLatency(posture));
        }
        Optional<PassRate<O>> mapped = PassRate.<O>fromPosture(posture);
        return mapped.map(pr -> (Criterion<O, ?>) pr);
    }

    private <O> PercentileLatency<O> deriveLatency(CriterionPosture posture) {
        var asserted = posture.assertedPercentiles().orElseThrow(() -> new IllegalStateException(
                "latency posture without asserted percentiles"));
        if (posture.kind() == CriterionPosture.Kind.LATENCY_EMPIRICAL) {
            PercentileKey[] arr = asserted.toArray(new PercentileKey[0]);
            PercentileKey first = arr[0];
            PercentileKey[] rest = new PercentileKey[arr.length - 1];
            System.arraycopy(arr, 1, rest, 0, rest.length);
            double confidence = posture.confidenceFloor().isPresent()
                    ? posture.confidenceFloor().getAsDouble()
                    : org.javai.punit.statistics.StatisticalDefaults.DEFAULT_CONFIDENCE;
            return PercentileLatency.<O>empirical(confidence, first, rest);
        }
        // LATENCY_CONTRACTUAL
        LatencySpec spec = posture.latencySpec().orElseThrow(() -> new IllegalStateException(
                "LATENCY_CONTRACTUAL posture without LatencySpec"));
        ThresholdOrigin origin = posture.origin().orElseThrow(() -> new IllegalStateException(
                "LATENCY_CONTRACTUAL posture without origin"));
        return PercentileLatency.<O>meeting(spec, origin);
    }
}
