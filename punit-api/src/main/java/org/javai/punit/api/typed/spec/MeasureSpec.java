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

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;

/**
 * A single-configuration measurement experiment that produces a
 * baseline spec artefact.
 *
 * <p>Constructed via {@link #measuring(Sampling, Object)}. The
 * {@link Sampling} carries the factor-free sample-production
 * parameters (use case factory, inputs, sample count, budgets,
 * exception policy); the second argument is the factor bundle the
 * measure runs against. The measure spec itself carries only the
 * measure-specific overlay (experiment id, expected outputs for
 * instance conformance, matcher, baseline expiration window).
 *
 * <p>Stage 2 implements the spec surface and strategy-method dispatch;
 * artefact serialisation to YAML lands in Stage 4, at which point
 * {@link #conclude()} becomes load-bearing. For now it returns an
 * {@link ExperimentResult} with a placeholder message and the path
 * where the baseline <em>would</em> be written.
 */
public final class MeasureSpec<FT, IT, OT> implements Spec, TypedSpec<FT, IT, OT> {

    @Override
    public <R> R dispatch(Dispatcher<R> dispatcher) {
        return dispatcher.apply(this);
    }


    private final Sampling<FT, IT, OT> sampling;
    private final FT factors;
    private final List<OT> expectedOutputs;
    private final Optional<ValueMatcher<OT>> matcher;
    private final String experimentId;
    private final Optional<Integer> expiresInDays;

    private Optional<SampleSummary<OT>> lastSummary = Optional.empty();

    private MeasureSpec(Builder<FT, IT, OT> b) {
        this.sampling = b.sampling;
        this.factors = b.factors;
        this.expectedOutputs = b.expected;
        this.matcher = Optional.ofNullable(b.matcher);
        this.experimentId = b.experimentId != null ? b.experimentId : defaultExperimentId();
        this.expiresInDays = Optional.ofNullable(b.expiresInDays);
    }

    /**
     * Entry point — compose a measure experiment over a factor-free
     * {@link Sampling} and the factor bundle it should run against.
     */
    public static <FT, IT, OT> Builder<FT, IT, OT> measuring(
            Sampling<FT, IT, OT> sampling, FT factors) {
        return new Builder<>(
                Objects.requireNonNull(sampling, "sampling"),
                Objects.requireNonNull(factors, "factors"));
    }

    // ── Overlay accessors ───────────────────────────────────────────

    /** The factor-free sampling backing this measure. */
    public Sampling<FT, IT, OT> sampling() {
        return sampling;
    }

    /** The factor bundle this measure runs against. */
    public FT factors() {
        return factors;
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

    // ── Spec — run-loop delegates to the sampling ─────

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return sampling.useCaseFactory();
    }

    @Override public Optional<ValueMatcher<OT>> matcher() {
        return matcher;
    }

    @Override public Iterator<Configuration<FT, IT, OT>> configurations() {
        return List.of(new Configuration<>(
                factors, sampling.inputs(), expectedOutputs, sampling.samples())).iterator();
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

    @Override public Optional<Duration> timeBudget() { return sampling.timeBudget(); }
    @Override public OptionalLong tokenBudget() { return sampling.tokenBudget(); }
    @Override public long tokenCharge() { return sampling.tokenCharge(); }
    @Override public BudgetExhaustionPolicy budgetPolicy() { return sampling.budgetPolicy(); }
    @Override public ExceptionPolicy exceptionPolicy() { return sampling.exceptionPolicy(); }
    @Override public int maxExampleFailures() { return sampling.maxExampleFailures(); }

    // ── Convenience delegates ───────────────────────────────────────

    public int samples() { return sampling.samples(); }
    public List<IT> inputs() { return sampling.inputs(); }

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

        private final Sampling<FT, IT, OT> sampling;
        private final FT factors;
        private List<OT> expected = List.of();
        private ValueMatcher<OT> matcher;
        private String experimentId;
        private Integer expiresInDays;

        private Builder(Sampling<FT, IT, OT> sampling, FT factors) {
            this.sampling = sampling;
            this.factors = factors;
        }

        public Builder<FT, IT, OT> experimentId(String id) {
            this.experimentId = Objects.requireNonNull(id, "experimentId");
            return this;
        }

        /**
         * Expected outputs, parallel to {@code sampling.inputs()}.
         * Enables instance-conformance checking when supplied. The list
         * must be the same length as the sampling's inputs — a mismatch
         * is rejected at {@link #build()} with
         * {@link IllegalStateException}.
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
            if (!expected.isEmpty() && expected.size() != sampling.inputs().size()) {
                throw new IllegalStateException(
                        "expectedOutputs (" + expected.size() + ") and sampling.inputs() ("
                                + sampling.inputs().size() + ") must be the same length");
            }
            if (!expected.isEmpty() && matcher == null) {
                matcher = ValueMatcher.equality();
            }
            return new MeasureSpec<>(this);
        }
    }
}
