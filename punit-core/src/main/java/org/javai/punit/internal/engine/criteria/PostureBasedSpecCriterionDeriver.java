package org.javai.punit.internal.engine.criteria;

import java.util.Optional;

import org.javai.punit.api.criterion.CriterionPosture;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.SpecCriterionDeriver;

/**
 * The framework's default {@link SpecCriterionDeriver} — maps
 * statistical postures to {@link PassRate} variants.
 *
 * <p>Registered via {@code META-INF/services} so the api-side builder
 * finds it through {@link java.util.ServiceLoader} without importing
 * the {@code internal} namespace.
 */
public final class PostureBasedSpecCriterionDeriver implements SpecCriterionDeriver {

    @Override
    @SuppressWarnings("unchecked")
    public <O> Optional<Criterion<O, ?>> derive(CriterionPosture posture) {
        Optional<PassRate<O>> mapped = PassRate.<O>fromPosture(posture);
        return mapped.map(pr -> (Criterion<O, ?>) pr);
    }
}
