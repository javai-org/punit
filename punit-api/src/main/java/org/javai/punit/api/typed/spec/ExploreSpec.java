package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;

/**
 * A grid-of-configurations experiment: runs {@code sampling.samples()}
 * samples against each factor bundle in the explored grid and records
 * the per-config outcome.
 *
 * <p>Constructed via {@link #exploring(Sampling)}. All sample-
 * production knobs live on the {@link Sampling}; the explore spec
 * itself carries only the varied factor list and the experiment id.
 *
 * <p>The public class carries no type parameters — composition-time
 * type safety lives on the typed {@link Builder}, and the engine
 * recovers the typed view via {@link Spec#dispatch(Dispatcher)}.
 */
public final class ExploreSpec implements Spec {

    private final Internal<?, ?, ?> internal;

    private ExploreSpec(Internal<?, ?, ?> internal) {
        this.internal = internal;
    }

    /** Entry point — compose an explore experiment over a Sampling. */
    public static <FT, IT, OT> Builder<FT, IT, OT> exploring(Sampling<FT, IT, OT> sampling) {
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

    public String experimentId() { return internal.experimentId; }
    public int samplesPerConfig() { return internal.shape.samples(); }

    // ── Typed internal delegate (engine-facing) ─────────────────────

    private static final class Internal<FT, IT, OT> implements TypedSpec<FT, IT, OT> {

        private final Sampling<FT, IT, OT> shape;
        private final List<FT> factors;
        private final String experimentId;

        private final Map<FT, SampleSummary<OT>> perConfig = new LinkedHashMap<>();

        private Internal(Builder<FT, IT, OT> b) {
            this.shape = b.shape;
            this.factors = b.factors;
            this.experimentId = b.experimentId != null
                    ? b.experimentId
                    : "explore-" + Instant.now().toEpochMilli();
        }

        @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
            return shape.useCaseFactory();
        }

        @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
            List<Configuration<FT, IT, OT>> configs = new ArrayList<>(factors.size());
            for (FT ft : factors) {
                configs.add(Configuration.of(ft, shape.inputs(), shape.samples()));
            }
            return configs.iterator();
        }

        @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
            perConfig.put(config.factors(), summary);
        }

        @Override public EngineResult conclude() {
            Path dir = Paths.get("explorations", experimentId);
            String message = "explore artefact (stage 2 placeholder); configurations="
                    + perConfig.size();
            return new ExperimentResult(message, dir);
        }

        @Override public Optional<Duration> timeBudget() { return shape.timeBudget(); }
        @Override public OptionalLong tokenBudget() { return shape.tokenBudget(); }
        @Override public long tokenCharge() { return shape.tokenCharge(); }
        @Override public BudgetExhaustionPolicy budgetPolicy() { return shape.budgetPolicy(); }
        @Override public ExceptionPolicy exceptionPolicy() { return shape.exceptionPolicy(); }
        @Override public int maxExampleFailures() { return shape.maxExampleFailures(); }
    }

    public static final class Builder<FT, IT, OT> {

        private final Sampling<FT, IT, OT> shape;
        private List<FT> factors;
        private String experimentId;

        private Builder(Sampling<FT, IT, OT> shape) {
            this.shape = shape;
        }

        public Builder<FT, IT, OT> factors(List<FT> grid) {
            Objects.requireNonNull(grid, "factors");
            if (grid.isEmpty()) {
                throw new IllegalArgumentException("factors must be non-empty");
            }
            this.factors = List.copyOf(grid);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> factors(FT... grid) {
            Objects.requireNonNull(grid, "factors");
            return factors(List.of(grid));
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public ExploreSpec build() {
            if (factors == null) {
                throw new IllegalStateException("factors is required — call .factors(...)");
            }
            return new ExploreSpec(new Internal<>(this));
        }
    }
}
