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

import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.spec.FactorMutator.IterationResult;

/**
 * An iterative factor-space search. Each iteration runs
 * {@code samplesPerIteration} samples, scores the outcome, and asks
 * the {@link FactorMutator} for the next factor record. The iterator
 * stops when:
 *
 * <ul>
 *   <li>the mutator returns {@code null};</li>
 *   <li>{@code maxIterations} is reached;</li>
 *   <li>{@code noImprovementWindow} consecutive iterations fail to
 *       improve the best score.</li>
 * </ul>
 *
 * <p>Stage 2 delivers the builder, the iterator, and a placeholder
 * artefact outcome. YAML serialisation lands in Stage 4.
 */
public final class OptimizeSpec<FT, IT, OT> implements Spec<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final FT initialFactors;
    private final List<IT> inputs;
    private final FactorMutator<FT> mutator;
    private final Scorer scorer;
    private final Objective objective;
    private final int samplesPerIteration;
    private final int maxIterations;
    private final int noImprovementWindow;
    private final String experimentId;

    private final List<IterationResult<FT>> history = new ArrayList<>();

    private final ResourceControls resourceControls;

    private OptimizeSpec(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.initialFactors = b.initialFactors;
        this.inputs = b.inputs;
        this.mutator = b.mutator;
        this.scorer = b.scorer;
        this.objective = b.objective;
        this.samplesPerIteration = b.samplesPerIteration;
        this.maxIterations = b.maxIterations;
        this.noImprovementWindow = b.noImprovementWindow;
        this.experimentId = b.experimentId;
        this.resourceControls = b.resources.build();
    }

    public static <FT, IT, OT> Builder<FT, IT, OT> builder() {
        return new Builder<>();
    }

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return new AdaptiveIterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        double score = scorer.score(summary);
        history.add(new IterationResult<>(config.factors(), score));
    }

    @Override public EngineOutcome conclude() {
        IterationResult<FT> best = bestSoFar();
        Path dir = Paths.get("optimizations", experimentId);
        String message = "optimize artefact (stage 2 placeholder); iterations="
                + history.size()
                + (best == null ? "" : ", bestScore=" + String.format("%.3f", best.score()));
        return new EngineOutcome.Artefact(message, dir);
    }

    public List<IterationResult<FT>> history() {
        return Collections.unmodifiableList(history);
    }

    // ── Stage-3 spec-interface accessors ─────────────────────────────

    @Override public Optional<Duration> timeBudget() { return resourceControls.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return resourceControls.tokenBudget(); }
    @Override public long tokenCharge() { return resourceControls.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return resourceControls.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return resourceControls.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return resourceControls.maxExampleFailures(); }
    @Override public LatencySpec latency() { return resourceControls.latency(); }

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
        return objective == Objective.MAXIMIZE ? a > b : a < b;
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
            return Configuration.of(factors, inputs, samplesPerIteration);
        }

        private void refresh() {
            if (next != null) {
                return;
            }
            if (issued >= maxIterations) {
                return;
            }
            // Update plateau detection against history.
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

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private FT initialFactors;
        private List<IT> inputs;
        private FactorMutator<FT> mutator;
        private Scorer scorer;
        private Objective objective = Objective.MAXIMIZE;
        private int samplesPerIteration = 20;
        private int maxIterations = 20;
        private int noImprovementWindow = 5;
        private String experimentId;
        private final ResourceControlsBuilder resources = new ResourceControlsBuilder();

        private Builder() {}

        public Builder<FT, IT, OT> timeBudget(Duration budget) {
            resources.timeBudget(budget);
            return this;
        }

        public Builder<FT, IT, OT> tokenBudget(long tokens) {
            resources.tokenBudget(tokens);
            return this;
        }

        public Builder<FT, IT, OT> tokenCharge(long tokens) {
            resources.tokenCharge(tokens);
            return this;
        }

        public Builder<FT, IT, OT> onBudgetExhausted(BudgetExhaustionPolicy policy) {
            resources.onBudgetExhausted(policy);
            return this;
        }

        public Builder<FT, IT, OT> onException(ExceptionPolicy policy) {
            resources.onException(policy);
            return this;
        }

        public Builder<FT, IT, OT> maxExampleFailures(int cap) {
            resources.maxExampleFailures(cap);
            return this;
        }

        public Builder<FT, IT, OT> latency(LatencySpec spec) {
            resources.latency(spec);
            return this;
        }

        public Builder<FT, IT, OT> useCaseFactory(Function<FT, UseCase<FT, IT, OT>> factory) {
            this.useCaseFactory = Objects.requireNonNull(factory, "useCaseFactory");
            return this;
        }

        public Builder<FT, IT, OT> initialFactors(FT factors) {
            this.initialFactors = Objects.requireNonNull(factors, "initialFactors");
            return this;
        }

        public Builder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(inputs));
        }

        public Builder<FT, IT, OT> mutator(FactorMutator<FT> m) {
            this.mutator = Objects.requireNonNull(m, "mutator");
            return this;
        }

        public Builder<FT, IT, OT> scorer(Scorer s) {
            this.scorer = Objects.requireNonNull(s, "scorer");
            return this;
        }

        public Builder<FT, IT, OT> objective(Objective o) {
            this.objective = Objects.requireNonNull(o, "objective");
            return this;
        }

        public Builder<FT, IT, OT> samplesPerIteration(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("samplesPerIteration must be ≥ 1");
            }
            this.samplesPerIteration = n;
            return this;
        }

        public Builder<FT, IT, OT> maxIterations(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("maxIterations must be ≥ 1");
            }
            this.maxIterations = n;
            return this;
        }

        public Builder<FT, IT, OT> noImprovementWindow(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("noImprovementWindow must be ≥ 1");
            }
            this.noImprovementWindow = n;
            return this;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public OptimizeSpec<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (initialFactors == null) {
                throw new IllegalStateException("initialFactors is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalStateException("inputs is required and must be non-empty");
            }
            if (mutator == null) {
                throw new IllegalStateException("mutator is required");
            }
            if (scorer == null) {
                throw new IllegalStateException("scorer is required");
            }
            if (experimentId == null) {
                experimentId = "optimize-" + Instant.now().toEpochMilli();
            }
            return new OptimizeSpec<>(this);
        }
    }
}
