package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.spec.FactorMutator.IterationResult;

/**
 * An iterative factor-space search. Each iteration runs
 * {@code sampling.samples()} samples under the current factor bundle,
 * scores the outcome, and asks the {@link FactorMutator} for the
 * next factor record. The iterator stops when:
 *
 * <ul>
 *   <li>the mutator returns {@code null};</li>
 *   <li>{@code maxIterations} is reached;</li>
 *   <li>{@code noImprovementWindow} consecutive iterations fail to
 *       improve the best score.</li>
 * </ul>
 *
 * <p>Constructed via {@link #optimizing(Sampling)}. All sample-
 * production knobs live on the {@link Sampling}; the optimize spec
 * carries only the search-specific knobs.
 *
 * <p>The optimisation direction is expressed by which builder method
 * the author calls — {@link Builder#maximize(Scorer)} or
 * {@link Builder#minimize(Scorer)} — rather than by a separate
 * direction enum. The two methods are mutually exclusive at the API
 * level: one of them must be called and the last one wins.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed {@link Builder}, and the engine
 * recovers the typed view via {@link Spec#dispatch(Dispatcher)}.
 */
public final class OptimizeSpec implements Spec {

    private final Internal<?, ?, ?> internal;

    private OptimizeSpec(Internal<?, ?, ?> internal) {
        this.internal = internal;
    }

    /** Entry point — compose an optimize experiment over a Sampling. */
    public static <FT, IT, OT> Builder<FT, IT, OT> optimizing(Sampling<FT, IT, OT> sampling) {
        return new Builder<>(Objects.requireNonNull(sampling, "sampling"));
    }

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return doDispatch(internal, dispatcher);
    }

    private static <FT, IT, OT, R> R doDispatch(Internal<FT, IT, OT> typed, Dispatcher<R> d) {
        return d.apply(typed);
    }

    // ── Public scalar accessors (no type parameters) ───────────────

    public int maxIterations() { return internal.maxIterations; }
    public int noImprovementWindow() { return internal.noImprovementWindow; }
    public String experimentId() { return internal.experimentId; }
    public boolean isMaximizing() { return internal.maximizing; }
    public int samplesPerIteration() { return internal.shape.samples(); }

    /** The iteration history — wildcard-typed at the public surface. */
    public List<IterationResult<?>> history() {
        return List.copyOf(internal.history);
    }

    // ── Typed internal delegate (engine-facing) ─────────────────────

    private static final class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        private final Sampling<FT, IT, OT> shape;
        private final FT initialFactors;
        private final FactorMutator<FT> mutator;
        private final Scorer scorer;
        private final boolean maximizing;
        private final int maxIterations;
        private final int noImprovementWindow;
        private final String experimentId;

        private final List<IterationResult<FT>> history = new ArrayList<>();

        private Internal(Builder<FT, IT, OT> b) {
            this.shape = b.shape;
            this.initialFactors = b.initialFactors;
            this.mutator = b.mutator;
            this.scorer = b.scorer;
            this.maximizing = b.maximizing;
            this.maxIterations = b.maxIterations;
            this.noImprovementWindow = b.noImprovementWindow;
            this.experimentId = b.experimentId != null
                    ? b.experimentId
                    : "optimize-" + Instant.now().toEpochMilli();
        }

        @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
            return shape.useCaseFactory();
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            return new AdaptiveIterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            double score = scorer.score(summary);
            history.add(new IterationResult<>(config.factors(), score));
        }

        @Override public EngineResult conclude() {
            IterationResult<FT> best = bestSoFar();
            Path dir = Paths.get("optimizations", experimentId);
            String message = "optimize artefact (stage 2 placeholder); iterations="
                    + history.size()
                    + (best == null ? "" : ", bestScore=" + String.format("%.3f", best.score()));
            return new ExperimentResult(message, dir);
        }

        @Override public Optional<Duration> timeBudget() { return shape.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return shape.tokenBudget(); }
        @Override public long tokenCharge() { return shape.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return shape.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return shape.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return shape.maxExampleFailures(); }

        private IterationResult<FT> bestSoFar() {
            IterationResult<FT> best = null;
            for (IterationResult<FT> r : history) {
                if (best == null || isBetter(r.score(), best.score())) {
                    best = r;
                }
            }
            return best;
        }

        private boolean isBetter(double a, double b) {
            return maximizing ? a > b : a < b;
        }

        private final class AdaptiveIterator implements Iterator<Configuration<FT, IT, OT>> {

            private FT next = initialFactors;
            private int issued = 0;
            private int iterationsSinceImprovement = 0;
            private double bestScoreSeen = Double.NaN;

            @Override public boolean hasNext() {
                refresh();
                return next != null;
            }

            @Override public Configuration<FT, IT, OT> next() {
                refresh();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                FT factors = next;
                next = null;
                issued++;
                return Configuration.of(factors, shape.inputs(), shape.samples());
            }

            private void refresh() {
                if (next != null) {
                    return;
                }
                if (issued >= maxIterations) {
                    return;
                }
                if (!history.isEmpty()) {
                    IterationResult<FT> last = history.get(history.size() - 1);
                    if (Double.isNaN(bestScoreSeen)
                            || isBetter(last.score(), bestScoreSeen)) {
                        bestScoreSeen = last.score();
                        iterationsSinceImprovement = 0;
                    } else {
                        iterationsSinceImprovement++;
                    }
                    if (iterationsSinceImprovement >= noImprovementWindow) {
                        return;
                    }
                    FT current = last.factors();
                    FT candidate = mutator.next(current,
                            Collections.unmodifiableList(history));
                    next = candidate;
                }
            }
        }
    }

    public static final class Builder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> shape;
        private FT initialFactors;
        private FactorMutator<FT> mutator;
        private Scorer scorer;
        private boolean maximizing;
        private boolean directionSet;
        private int maxIterations = 20;
        private int noImprovementWindow = 5;
        private String experimentId;

        private Builder(Sampling<FT, IT, OT> shape) {
            this.shape = shape;
        }

        public Builder<FT, IT, OT> initialFactors(FT factors) {
            this.initialFactors = Objects.requireNonNull(factors, "initialFactors");
            return this;
        }

        public Builder<FT, IT, OT> mutator(FactorMutator<FT> m) {
            this.mutator = Objects.requireNonNull(m, "mutator");
            return this;
        }

        /**
         * Sets the scorer and declares the optimisation direction as
         * "maximise the score." Mutually exclusive with
         * {@link #minimize(Scorer)} — the last one called wins. One
         * of {@code maximize} / {@code minimize} is required.
         */
        public Builder<FT, IT, OT> maximize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = true;
            this.directionSet = true;
            return this;
        }

        /**
         * Sets the scorer and declares the optimisation direction as
         * "minimise the score." Mutually exclusive with
         * {@link #maximize(Scorer)} — the last one called wins. One
         * of {@code maximize} / {@code minimize} is required.
         */
        public Builder<FT, IT, OT> minimize(Scorer scorer) {
            this.scorer = Objects.requireNonNull(scorer, "scorer");
            this.maximizing = false;
            this.directionSet = true;
            return this;
        }

        public Builder<FT, IT, OT> maxIterations(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("maxIterations must be >= 1");
            }
            this.maxIterations = n;
            return this;
        }

        public Builder<FT, IT, OT> noImprovementWindow(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("noImprovementWindow must be >= 1");
            }
            this.noImprovementWindow = n;
            return this;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public OptimizeSpec build() {
            if (initialFactors == null) {
                throw new IllegalStateException("initialFactors is required");
            }
            if (mutator == null) {
                throw new IllegalStateException("mutator is required");
            }
            if (!directionSet) {
                throw new IllegalStateException(
                        "scorer is required — call .maximize(...) or .minimize(...)");
            }
            return new OptimizeSpec(new Internal<>(this));
        }
    }
}
