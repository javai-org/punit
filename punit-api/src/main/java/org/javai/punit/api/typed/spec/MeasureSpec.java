package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.Expectation;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;

/**
 * A single-configuration measurement experiment that produces a
 * baseline spec artefact.
 *
 * <p>Stage 2 implements the spec surface and the strategy-method
 * dispatch; artefact serialisation to YAML lands in Stage 4, at which
 * point {@link #conclude()} becomes load-bearing. For now it returns
 * an {@link ExperimentResult} with a placeholder message and the
 * path where the baseline *would* be written.
 */
public final class MeasureSpec<FT, IT, OT> implements DataGenerationSpec<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final FT factors;
    private final List<IT> inputs;
    private final List<OT> expected;
    private final Optional<ValueMatcher<OT>> matcher;
    private final int samples;
    private final String experimentId;
    private final ResourceControls resourceControls;

    private Optional<SampleSummary<OT>> lastSummary = Optional.empty();

    private MeasureSpec(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.factors = b.factors;
        this.inputs = b.inputs;
        this.expected = b.expected;
        this.matcher = Optional.ofNullable(b.matcher);
        this.samples = b.samples;
        this.experimentId = b.experimentId;
        this.resourceControls = b.resources.build();
    }

    public static <FT, IT, OT> Builder<FT, IT, OT> builder() {
        return new Builder<>();
    }

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    @Override public Optional<ValueMatcher<OT>> matcher() {
        return matcher;
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return List.of(new Configuration<>(factors, inputs, expected, samples)).iterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        this.lastSummary = Optional.of(summary);
    }

    @Override public EngineResult conclude() {
        FactorBundle bundle = FactorBundle.of(factors);
        Path path = defaultBaselinePath(experimentId, bundle);
        String message = "measure baseline (stage 2 placeholder — serialisation lands in stage 4); "
                + "samples=" + lastSummary.map(SampleSummary::total).orElse(0)
                + ", passRate=" + lastSummary.map(s -> String.format("%.3f", s.passRate())).orElse("n/a");
        return new ExperimentResult(message, path);
    }

    // ── Stage-3 spec-interface accessors, delegated to ResourceControls ────

    @Override public Optional<Duration> timeBudget() { return resourceControls.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return resourceControls.tokenBudget(); }
    @Override public long tokenCharge() { return resourceControls.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return resourceControls.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return resourceControls.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return resourceControls.maxExampleFailures(); }
    @Override public LatencySpec latency() { return resourceControls.latency(); }

    public String experimentId() {
        return experimentId;
    }

    public int samples() {
        return samples;
    }

    public FT factors() {
        return factors;
    }

    public List<IT> inputs() {
        return inputs;
    }

    /**
     * The summary of the most recent run, if any — useful for engine
     * integration tests asserting on termination reason, latency
     * result, or failure counts without wrapping the spec.
     */
    public Optional<SampleSummary<OT>> lastSummary() {
        return lastSummary;
    }

    static Path defaultBaselinePath(String experimentId, FactorBundle bundle) {
        String filename = experimentId;
        if (!bundle.isEmpty()) {
            filename = filename + "-" + bundle.bundleHash();
        }
        return Paths.get("specs", filename + ".yaml");
    }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
        private FT factors;
        private List<IT> inputs;
        private List<OT> expected = List.of();
        private ValueMatcher<OT> matcher;
        private int samples = 1000;
        private String experimentId;
        private boolean inputsOrExpectationsSet;
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

        public Builder<FT, IT, OT> factors(FT factors) {
            this.factors = Objects.requireNonNull(factors, "factors");
            return this;
        }

        public Builder<FT, IT, OT> inputs(List<IT> inputs) {
            Objects.requireNonNull(inputs, "inputs");
            guardInputsVsExpectations();
            this.inputs = List.copyOf(inputs);
            this.expected = List.of();
            this.inputsOrExpectationsSet = true;
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(inputs));
        }

        /**
         * Supply input + expected pairs for instance-conformance
         * checking. Alternative to {@link #inputs(List)}; calling both
         * on the same builder is a build-time error.
         *
         * <p>The engine will run the spec's {@link #matcher(ValueMatcher)
         * matcher} (default {@link ValueMatcher#equality()}) for each
         * sample and attach a {@code MatchResult} to the outcome. A
         * failed match counts as a failed sample.
         */
        public Builder<FT, IT, OT> expectations(List<Expectation<IT, OT>> pairs) {
            Objects.requireNonNull(pairs, "pairs");
            guardInputsVsExpectations();
            if (pairs.isEmpty()) {
                throw new IllegalArgumentException("expectations must be non-empty");
            }
            List<IT> in = new ArrayList<>(pairs.size());
            List<OT> exp = new ArrayList<>(pairs.size());
            for (Expectation<IT, OT> e : pairs) {
                in.add(e.input());
                exp.add(e.expected());
            }
            this.inputs = List.copyOf(in);
            this.expected = List.copyOf(exp);
            this.inputsOrExpectationsSet = true;
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> expectations(Expectation<IT, OT>... pairs) {
            return expectations(List.of(pairs));
        }

        /**
         * Instance-conformance matcher, invoked on each sample when
         * expectations are supplied. Defaults to
         * {@link ValueMatcher#equality()}.
         */
        public Builder<FT, IT, OT> matcher(ValueMatcher<OT> matcher) {
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            return this;
        }

        public Builder<FT, IT, OT> samples(int samples) {
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be ≥ 1");
            }
            this.samples = samples;
            return this;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        public MeasureSpec<FT, IT, OT> build() {
            if (useCaseFactory == null) {
                throw new IllegalStateException("useCaseFactory is required");
            }
            if (factors == null) {
                throw new IllegalStateException("factors is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalStateException(
                        "inputs is required — call .inputs(...) or .expectations(...)");
            }
            if (!expected.isEmpty() && matcher == null) {
                matcher = ValueMatcher.equality();
            }
            if (experimentId == null) {
                experimentId = defaultExperimentId();
            }
            return new MeasureSpec<>(this);
        }

        private void guardInputsVsExpectations() {
            if (inputsOrExpectationsSet) {
                throw new IllegalStateException(
                        "cannot call both .inputs(...) and .expectations(...) on the same builder");
            }
        }

        private static String defaultExperimentId() {
            return "measure-" + Instant.now().toEpochMilli();
        }
    }
}
