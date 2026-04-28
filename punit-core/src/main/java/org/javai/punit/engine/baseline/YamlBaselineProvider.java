package org.javai.punit.engine.baseline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.BaselineStatistics;

/**
 * Adapts a {@link BaselineResolver} (file-system / YAML-aware) to the
 * {@link BaselineProvider} interface the typed-spec layer programs
 * against. The fingerprint computation lives in
 * {@link FactorsFingerprint} so the read-side and the write-side use
 * the same algorithm by construction.
 */
public final class YamlBaselineProvider implements BaselineProvider {

    private final BaselineResolver resolver;

    public YamlBaselineProvider(Path baselineDir) {
        this(new BaselineResolver(Objects.requireNonNull(baselineDir, "baselineDir")));
    }

    public YamlBaselineProvider(BaselineResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        String fingerprint = FactorsFingerprint.of(factors);
        return resolver.resolve(useCaseId, fingerprint, criterionName, statisticsType,
                currentProfile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String useCaseId, FactorBundle factors,
            CovariateProfile currentProfile, List<Covariate> declarations) {
        String fingerprint = FactorsFingerprint.of(factors);
        return resolver.resolveInputsIdentity(useCaseId, fingerprint,
                currentProfile, declarations);
    }
}
