package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.UseCase;

/**
 * A single-configuration measurement experiment that produces a
 * baseline spec artefact.
 *
 * <p>Stage 2 implements the spec surface and the strategy-method
 * dispatch; artefact serialisation to YAML lands in Stage 4, at which
 * point {@link #conclude()} becomes load-bearing. For now it returns
 * an {@link EngineOutcome.Artefact} with a placeholder message and the
 * path where the baseline *would* be written.
 */
public final class MeasureSpec<FT, IT, OT> implements Spec<FT, IT, OT> {

    private final Function<FT, UseCase<FT, IT, OT>> useCaseFactory;
    private final FT factors;
    private final List<IT> inputs;
    private final int samples;
    private final String experimentId;

    private Optional<SampleSummary<OT>> lastSummary = Optional.empty();

    private MeasureSpec(Builder<FT, IT, OT> b) {
        this.useCaseFactory = b.useCaseFactory;
        this.factors = b.factors;
        this.inputs = b.inputs;
        this.samples = b.samples;
        this.experimentId = b.experimentId;
    }

    public static <FT, IT, OT> Builder<FT, IT, OT> builder() {
        return new Builder<>();
    }

    @Override public Function<FT, UseCase<FT, IT, OT>> useCaseFactory() {
        return useCaseFactory;
    }

    @Override public Iterator<Configuration<FT, IT>> configurations() {
        return List.of(new Configuration<>(factors, inputs, samples)).iterator();
    }

    @Override public void consume(Configuration<FT, IT> config, SampleSummary<OT> summary) {
        this.lastSummary = Optional.of(summary);
    }

    @Override public EngineOutcome conclude() {
        FactorBundle bundle = FactorBundle.of(factors);
        Path path = defaultBaselinePath(experimentId, bundle);
        String message = "measure baseline (stage 2 placeholder — serialisation lands in stage 4); "
                + "samples=" + lastSummary.map(SampleSummary::total).orElse(0)
                + ", passRate=" + lastSummary.map(s -> String.format("%.3f", s.passRate())).orElse("n/a");
        return new EngineOutcome.Artefact(message, path);
    }

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
        private int samples = 1000;
        private String experimentId;

        private Builder() {}

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
            this.inputs = List.copyOf(inputs);
            return this;
        }

        @SafeVarargs
        public final Builder<FT, IT, OT> inputs(IT... inputs) {
            return inputs(List.of(inputs));
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
                throw new IllegalStateException("inputs is required and must be non-empty");
            }
            if (experimentId == null) {
                experimentId = defaultExperimentId();
            }
            return new MeasureSpec<>(this);
        }

        private static String defaultExperimentId() {
            return "measure-" + Instant.now().toEpochMilli();
        }
    }
}
