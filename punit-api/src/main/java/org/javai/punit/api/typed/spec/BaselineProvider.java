package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Optional;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;

/**
 * The framework's read-side adapter for resolving baseline statistics
 * at criterion-evaluation time.
 *
 * <p>Conceptually a small functional interface (one query method per
 * concern); kept as a regular interface so the {@link #EMPTY} constant
 * has a stable place to live. Implementations live in the engine
 * layer (e.g. punit-core's {@code engine.baseline.YamlBaselineProvider});
 * the {@code punit-api} module declares only the contract.
 *
 * <p>The framework consults a {@code BaselineProvider} once per
 * empirical {@link Criterion} during {@link Spec#dispatch dispatch} of
 * a {@link ProbabilisticTest}'s {@code conclude} call, populating the
 * matching slot on the {@link EvaluationContext}.
 *
 * <h2>Lookup contract</h2>
 *
 * <p>The covariate-aware overloads ({@link #baselineFor(String, FactorBundle,
 * String, Class, CovariateProfile, List)} and the matching identity
 * lookup) are the primary contract. Implementations supply them.
 * The convenience overloads without {@code currentProfile} and
 * {@code declarations} default-delegate with {@link CovariateProfile#empty()}
 * and {@link List#of()}; covariate-aware implementations interpret
 * those defaults as the legacy ("use case declares no covariates")
 * path.
 *
 * <p>The framework wraps the underlying provider in a profile-bound
 * decorator before handing it to {@link Spec#dispatch} so that
 * spec implementations can call the convenience overloads and still
 * exercise covariate-aware lookup with the run's resolved profile.
 *
 * @see CovariateProfile
 * @see Covariate
 */
public interface BaselineProvider {

    /**
     * @param currentProfile the resolved covariate profile for the
     *                       current run; {@link CovariateProfile#empty()}
     *                       when the use case declared no covariates
     * @param declarations   the use case's covariate declarations,
     *                       in declaration order; {@link List#of()}
     *                       when the use case declared no covariates
     * @return the baseline statistics of the requested kind for the
     *         best-matching baseline per UC05, or {@link Optional#empty()}
     *         when none is selectable
     */
    <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations);

    /**
     * Covariate-aware identity lookup. Used for the cross-process
     * {@link EmpiricalChecks#inputsIdentityMatch} integrity check.
     */
    Optional<String> baselineInputsIdentityFor(
            String useCaseId,
            FactorBundle factors,
            CovariateProfile currentProfile,
            List<Covariate> declarations);

    /**
     * Convenience overload for callers that don't carry covariate
     * state. Equivalent to the covariate-aware overload with empty
     * profile + empty declarations — restricts selection to
     * baselines whose own profile is empty.
     */
    default <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return baselineFor(useCaseId, factors, criterionName, statisticsType,
                CovariateProfile.empty(), List.of());
    }

    /**
     * Convenience overload for callers that don't carry covariate
     * state. Same legacy semantics as the four-arg
     * {@link #baselineFor(String, FactorBundle, String, Class) baselineFor}.
     */
    default Optional<String> baselineInputsIdentityFor(
            String useCaseId, FactorBundle factors) {
        return baselineInputsIdentityFor(useCaseId, factors,
                CovariateProfile.empty(), List.of());
    }

    /**
     * The empty provider — always returns {@link Optional#empty()}.
     * Used by the engine's no-arg / unconfigured paths and by tests
     * that don't exercise empirical resolution.
     */
    BaselineProvider EMPTY = new BaselineProvider() {
        @Override
        public <S extends BaselineStatistics> Optional<S> baselineFor(
                String useCaseId,
                FactorBundle factors,
                String criterionName,
                Class<S> statisticsType,
                CovariateProfile currentProfile,
                List<Covariate> declarations) {
            return Optional.empty();
        }

        @Override
        public Optional<String> baselineInputsIdentityFor(
                String useCaseId, FactorBundle factors,
                CovariateProfile currentProfile, List<Covariate> declarations) {
            return Optional.empty();
        }
    };
}
