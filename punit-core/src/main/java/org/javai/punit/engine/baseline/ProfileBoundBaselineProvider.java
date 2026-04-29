package org.javai.punit.engine.baseline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;
import org.javai.punit.api.typed.spec.BaselineLookup;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.BaselineStatistics;

/**
 * Wraps a {@link BaselineProvider} with a captured covariate profile
 * and declarations so that legacy-shape lookups (no profile/declarations
 * arguments) become covariate-aware lookups against the captured
 * values.
 *
 * <p>The framework constructs one of these per probabilistic-test run
 * after resolving the run's covariate profile from the use case, so
 * that {@link org.javai.punit.api.typed.spec.ProbabilisticTest#dispatch}
 * implementations can call the convenience overloads on
 * {@code BaselineProvider} and still exercise covariate-aware
 * selection.
 *
 * <p>The covariate-aware overloads pass through with whatever profile
 * / declarations the caller supplies; only the legacy overloads get
 * the captured-profile substitution. This means a caller that
 * <em>already</em> has covariate state can still bypass the binding
 * and pass its own.
 */
public final class ProfileBoundBaselineProvider implements BaselineProvider {

    private final BaselineProvider delegate;
    private final CovariateProfile profile;
    private final List<Covariate> declarations;

    private ProfileBoundBaselineProvider(
            BaselineProvider delegate,
            CovariateProfile profile,
            List<Covariate> declarations) {
        this.delegate = delegate;
        this.profile = profile;
        this.declarations = declarations;
    }

    /**
     * @return a binding wrapper, or {@code delegate} unchanged when
     *         there is nothing to bind ({@code declarations} empty)
     */
    public static BaselineProvider bind(
            BaselineProvider delegate,
            CovariateProfile profile,
            List<Covariate> declarations) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(declarations, "declarations");
        if (declarations.isEmpty()) {
            // No covariates declared → legacy semantics suffice.
            // Avoiding the wrapper keeps stack traces and toString
            // honest for non-covariate use cases.
            return delegate;
        }
        return new ProfileBoundBaselineProvider(delegate,
                profile, List.copyOf(declarations));
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return delegate.baselineFor(useCaseId, factors, criterionName,
                statisticsType, currentProfile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String useCaseId, FactorBundle factors,
            CovariateProfile currentProfile, List<Covariate> declarations) {
        return delegate.baselineInputsIdentityFor(
                useCaseId, factors, currentProfile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return delegate.baselineFor(useCaseId, factors, criterionName,
                statisticsType, profile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String useCaseId, FactorBundle factors) {
        return delegate.baselineInputsIdentityFor(useCaseId, factors,
                profile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return delegate.baselineLookup(useCaseId, factors, criterionName,
                statisticsType, currentProfile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return delegate.baselineLookup(useCaseId, factors, criterionName,
                statisticsType, profile, declarations);
    }
}
