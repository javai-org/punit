package org.javai.punit.api.typed.spec;

import java.util.Optional;

import org.javai.punit.api.typed.FactorBundle;

/**
 * The framework's read-side adapter for resolving baseline statistics
 * at criterion-evaluation time.
 *
 * <p>Conceptually a small functional interface (one query method); kept
 * as a regular interface so the {@link #EMPTY} constant has a stable
 * place to live. Implementations live in the engine layer
 * (e.g. punit-core's {@code engine.baseline.YamlBaselineProvider}); the
 * {@code punit-api} module declares only the contract.
 *
 * <p>The framework consults a {@code BaselineProvider} once per
 * empirical {@link Criterion} during {@link Spec#dispatch dispatch} of
 * a {@link ProbabilisticTest}'s {@code conclude} call, populating the
 * matching slot on the {@link EvaluationContext}.
 *
 * <h2>Stage 4 lookup contract</h2>
 *
 * <p>Stage-4 implementations resolve by exact match on
 * <em>(use case id, factors fingerprint, criterion name)</em>. The
 * {@link FactorBundle} is the runtime carrier of the factors record;
 * implementations derive a fingerprint from
 * {@code String.valueOf(factors.value())} or equivalent, matching the
 * write-side fingerprint algorithm.
 *
 * <p>A future stage may add covariate-aware closest-match resolution;
 * this interface stays unchanged because the additional matching
 * happens entirely inside the implementation.
 */
public interface BaselineProvider {

    /**
     * @return the baseline statistics of the requested kind for the
     *         given (use case, factors, criterion) tuple, or
     *         {@link Optional#empty()} when no matching baseline is
     *         resolvable.
     */
    <S extends BaselineStatistics> Optional<S> baselineFor(
            String useCaseId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType);

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
                Class<S> statisticsType) {
            return Optional.empty();
        }
    };
}
