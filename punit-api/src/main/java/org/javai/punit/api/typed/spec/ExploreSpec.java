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

import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;

/**
 * A grid-of-configurations experiment: runs {@code samplesPerConfig}
 * samples against each {@link #factors()} entry and records the
 * per-config outcome.
 *
 * <p>Stage 2 surfaces the builder and strategy-method dispatch;
 * per-configuration YAML serialisation lands in Stage 4.
 */
public final class ExploreSpec<FT, IT, OT> implements Spec<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final List<FT> factors;
    private final List<IT> inputs;
    private final int samplesPerConfig;
    private final String experimentId;
    private final ResourceControls resourceControls;

    private final Map<FT, SampleSummary<OT>> perConfig = new LinkedHashMap<>();

    private ExploreSpec(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.factors = b.factors;
        this.inputs = b.inputs;
        this.samplesPerConfig = b.samplesPerConfig;
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
        List<Configuration<FT, IT, OT>> configs = new ArrayList<>(factors.size());
        for (FT ft : factors) {
            configs.add(Configuration.of(ft, inputs, samplesPerConfig));
        }
        return configs.iterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        perConfig.put(config.factors(), summary);
    }

    @Override public EngineOutcome conclude() {
        Path dir = Paths.get("explorations", experimentId);
        String message = "explore artefact (stage 2 placeholder); configurations="
                + perConfig.size();
        return new EngineOutcome.Artefact(message, dir);
    }

    public List<FT> factors() { return factors; }
    public int samplesPerConfig() { return samplesPerConfig; }
    public String experimentId() { return experimentId; }

    // ── Stage-3 spec-interface accessors ─────────────────────────────

    @Override public Optional<Duration> timeBudget() { return resourceControls.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return resourceControls.tokenBudget(); }
    @Override public long tokenCharge() { return resourceControls.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return resourceControls.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return resourceControls.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return resourceControls.maxExampleFailures(); }
    @Override public LatencySpec latency() { return resourceControls.latency(); }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private List<FT> factors;
        private List<IT> inputs;
        private int samplesPerConfig = 1;
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

        public Builder<FT, IT, OT> factors(List<FT> grid) {
            Objects.requireNonNull(grid, "factors");
            this.factors = List.copyOf(grid);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> factors(FT... grid) {
            return factors(List.of(grid));
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

        public Builder<FT, IT, OT> samplesPerConfig(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("samplesPerConfig must be ≥ 1");
            }
            this.samplesPerConfig = n;
            return this;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public ExploreSpec<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (factors == null || factors.isEmpty()) {
                throw new IllegalStateException("factors grid is required and must be non-empty");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalStateException("inputs is required and must be non-empty");
            }
            if (experimentId == null) {
                experimentId = "explore-" + Instant.now().toEpochMilli();
            }
            return new ExploreSpec<>(this);
        }
    }
}
