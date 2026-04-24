package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.DataGeneration;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;

/**
 * A single-configuration measurement experiment that produces a
 * baseline spec artefact.
 *
 * <p>Constructed via {@link #measuring(DataGeneration)}. The
 * {@link DataGeneration} carries all sample-production parameters
 * (use case factory, inputs, sample count, budgets, exception policy);
 * the measure spec itself carries only the measure-specific overlay
 * (experiment id, expected outputs for instance conformance, matcher,
 * baseline expiration window).
 *
 * <p>Stage 2 implements the spec surface and strategy-method dispatch;
 * artefact serialisation to YAML lands in Stage 4, at which point
 * {@link #conclude()} becomes load-bearing. For now it returns an
 * {@link ExperimentResult} with a placeholder message and the path
 * where the baseline <em>would</em> be written.
 */
public final class MeasureSpec<FT, IT, OT> implements DataGenerationSpec<FT, IT, OT> {

    private final DataGeneration<FT, IT, OT> plan;
    private final List<OT> expectedOutputs;
    private final Optional<ValueMatcher<OT>> matcher;
    private final String experimentId;
    private final Optional<Integer> expiresInDays;

    private Optional<SampleSummary<OT>> lastSummary = Optional.empty();

    private MeasureSpec(Builder<FT, IT, OT> b) {
        this.plan = b.plan;
        this.expectedOutputs = b.expected;
        this.matcher = Optional.ofNullable(b.matcher);
        this.experimentId = b.experimentId != null ? b.experimentId : defaultExperimentId();
        this.expiresInDays = Optional.ofNullable(b.expiresInDays);
    }

    /**
     * Entry point — compose a measure experiment over a pre-built
     * {@link DataGeneration}.
     */
    public static <FT, IT, OT> Builder<FT, IT, OT> measuring(DataGeneration<FT, IT, OT> plan) {
        return new Builder<>(Objects.requireNonNull(plan, "plan"));
    }

    // ── Overlay accessors ───────────────────────────────────────────

    /** The data-generation plan backing this measure. */
    public DataGeneration<FT, IT, OT> dataGeneration() {
        return plan;
    }

    /** The optional expected-output list for instance-conformance checking. */
    public Optional<List<OT>> expectedOutputs() {
        return expectedOutputs.isEmpty() ? Optional.empty() : Optional.of(expectedOutputs);
    }

    /** Optional baseline validity window in days. */
    public Optional<Integer> expiresInDays() {
        return expiresInDays;
    }

    /** The experiment id written into the baseline YAML metadata. */
    public String experimentId() {
        return experimentId;
    }

    // ── DataGenerationSpec — run-loop delegates to the plan ─────────

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return plan.useCaseFactory();
    }

    @Override public Optional<ValueMatcher<OT>> matcher() {
        return matcher;
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return List.of(new Configuration<>(
                plan.factors(), plan.inputs(), expectedOutputs, plan.samples())).iterator();
    }

    @Override public void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary) {
        this.lastSummary = Optional.of(summary);
    }

    @Override public EngineResult conclude() {
        FactorBundle bundle = FactorBundle.of(plan.factors());
        Path path = defaultBaselinePath(experimentId, bundle);
        String message = "measure baseline (stage 2 placeholder — serialisation lands in stage 4); "
                + "samples=" + lastSummary.map(SampleSummary::total).orElse(0)
                + ", passRate=" + lastSummary.map(s -> String.format("%.3f", s.passRate())).orElse("n/a");
        return new ExperimentResult(message, path);
    }

    @Override public Optional<Duration> timeBudget() { return plan.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return plan.tokenBudget(); }
    @Override public long tokenCharge() { return plan.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return plan.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return plan.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return plan.maxExampleFailures(); }
    @Override public LatencySpec latency() { return LatencySpec.disabled(); }

    // ── Convenience delegates (still useful to callers) ─────────────

    public int samples() { return plan.samples(); }
    public FT factors() { return plan.factors(); }
    public List<IT> inputs() { return plan.inputs(); }

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

    private static String defaultExperimentId() {
        return "measure-" + Instant.now().toEpochMilli();
    }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder<FT, IT, OT> {

        private final DataGeneration<FT, IT, OT> plan;
        private List<OT> expected = List.of();
        private ValueMatcher<OT> matcher;
        private String experimentId;
        private Integer expiresInDays;

        private Builder(DataGeneration<FT, IT, OT> plan) {
            this.plan = plan;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        /**
         * Expected outputs, parallel to {@code plan.inputs()}. Enables
         * instance-conformance checking when supplied. The list must be
         * the same length as the plan's inputs — a mismatch is rejected
         * at {@link #build()} with {@link IllegalStateException}.
         */
        public Builder<FT, IT, OT> expectedOutputs(List<OT> outputs) {
            Objects.requireNonNull(outputs, "outputs");
            this.expected = List.copyOf(outputs);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> expectedOutputs(OT... outputs) {
            return expectedOutputs(List.of(outputs));
        }

        /**
         * Instance-conformance matcher, invoked per sample when
         * {@code expectedOutputs} is supplied. Defaults to
         * {@link ValueMatcher#equality()}.
         */
        public Builder<FT, IT, OT> matcher(ValueMatcher<OT> matcher) {
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            return this;
        }

        /** Baseline validity window. {@code 0} means no expiration. */
        public Builder<FT, IT, OT> expiresInDays(int days) {
            if (days < 0) {
                throw new IllegalArgumentException("expiresInDays must be non-negative, got " + days);
            }
            this.expiresInDays = days;
            return this;
        }

        public MeasureSpec<FT, IT, OT> build() {
            if (!expected.isEmpty() && expected.size() != plan.inputs().size()) {
                throw new IllegalStateException(
                        "expectedOutputs (" + expected.size() + ") and plan.inputs() ("
                                + plan.inputs().size() + ") must be the same length");
            }
            if (!expected.isEmpty() && matcher == null) {
                matcher = ValueMatcher.equality();
            }
            return new MeasureSpec<>(this);
        }
    }
}
