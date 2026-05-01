package org.javai.punit.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.InputSupplier;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.Configuration;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.LatencyStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Spec;
import org.javai.punit.api.spec.TypedSpec;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.engine.covariate.CovariateResolver;

/**
 * Translates a completed MEASURE {@link Experiment} into a
 * {@link BaselineRecord} on disk, by way of the engine's
 * {@link BaselineWriter}.
 *
 * <p>Called from {@link PUnit.MeasureBuilder#run} after the engine
 * finishes its sampling loop. The record carries both
 * {@link PassRateStatistics} and {@link LatencyStatistics} so any
 * future empirical criterion (CR02 or CR03 today, future kinds
 * additively) can read what it needs from the same baseline file.
 *
 * <p>Identity fields:
 *
 * <ul>
 *   <li>{@code useCaseId} — from
 *       {@code spec.useCaseFactory().apply(factors).id()}</li>
 *   <li>{@code factorsFingerprint} —
 *       {@link FactorsFingerprint#of(FactorBundle)}</li>
 *   <li>{@code inputsIdentity} — recomputed by content hash via
 *       {@link InputSupplier}, matching what a paired test's
 *       {@link InputSupplier} produces from the same input list</li>
 *   <li>{@code methodName} — the experiment's
 *       {@code experimentId} (no JUnit method context is on hand
 *       in the static-helper path; the field is retained on disk
 *       for forward-compatible filename uniqueness but is not part
 *       of the resolver's lookup key)</li>
 *   <li>{@code generatedAt} — {@code Instant.now()}</li>
 * </ul>
 */
final class BaselineEmitter {

    private BaselineEmitter() { }

    static void emit(Experiment experiment, Path baselineDir) {
        if (experiment.kind() != Experiment.Kind.MEASURE) {
            // Only MEASURE produces a baseline. The only caller is
            // PUnit.MeasureBuilder.run(), which by construction passes a
            // MEASURE experiment — reaching this branch is a programming
            // error, not a runtime condition.
            throw new IllegalArgumentException(
                    "BaselineEmitter.emit only accepts MEASURE-flavour Experiments; got "
                            + experiment.kind()
                            + ". EXPLORE / OPTIMIZE produce their own artefacts and must not "
                            + "be routed through the baseline emitter.");
        }
        SampleSummary<?> summary = experiment.lastSummary().orElseThrow(() ->
                new IllegalStateException(
                        "MEASURE experiment has no recorded summary — the engine did not "
                                + "consume the spec before emission. This is a framework "
                                + "invariant violation."));
        if (summary.total() == 0) {
            throw new IllegalStateException(
                    "MEASURE experiment recorded zero samples — nothing to baseline. "
                            + "Check the spec's sample count and budget configuration.");
        }
        BaselineRecord record = experiment.dispatch(new Spec.Dispatcher<>() {
            @Override
            public <FT, IT, OT> BaselineRecord apply(TypedSpec<FT, IT, OT> typed) {
                return composeRecord(typed, summary, experiment.experimentId());
            }
        });
        writeRecord(record, baselineDir);
    }

    @SuppressWarnings("unchecked")
    private static <FT, IT, OT> BaselineRecord composeRecord(
            TypedSpec<FT, IT, OT> typed,
            SampleSummary<?> rawSummary,
            String experimentId) {
        Iterator<Configuration<FT, IT, OT>> configs = typed.configurations();
        if (!configs.hasNext()) {
            throw new IllegalStateException(
                    "MEASURE produced no configuration — typed spec is malformed");
        }
        Configuration<FT, IT, OT> cfg = configs.next();
        FT factors = cfg.factors();
        UseCase<FT, IT, OT> useCase = typed.useCaseFactory().apply(factors);
        String useCaseId = useCase.id();
        String factorsFingerprint = FactorsFingerprint.of(FactorBundle.of(factors));
        String inputsIdentity = InputSupplier.from(cfg::inputs).identity();

        SampleSummary<OT> summary = (SampleSummary<OT>) rawSummary;
        int total = summary.total();
        Map<String, BaselineStatistics> stats = new LinkedHashMap<>();
        stats.put("bernoulli-pass-rate",
                new PassRateStatistics((double) summary.successes() / (double) total, total));
        LatencyResult latency = summary.latencyResult();
        if (latency.sampleCount() > 0) {
            stats.put("percentile-latency", new LatencyStatistics(latency, total));
        }

        // Per UC04, the resolved covariate profile is part of the
        // baseline's identity. The use case's declarations + custom
        // resolvers feed the resolver; the resulting profile is
        // stamped into the BaselineRecord and surfaces as an EX09
        // covariate-hash tail in the filename, plus a covariates:
        // block in the YAML body.
        List<Covariate> declarations = useCase.covariates();
        CovariateProfile profile = declarations.isEmpty()
                ? CovariateProfile.empty()
                : CovariateResolver.defaults()
                        .resolve(declarations, useCase.customCovariateResolvers());

        return new BaselineRecord(
                useCaseId,
                experimentId,
                factorsFingerprint,
                inputsIdentity,
                total,
                Instant.now(),
                stats,
                profile);
    }

    private static void writeRecord(BaselineRecord record, Path baselineDir) {
        try {
            new BaselineWriter().write(record, baselineDir);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write baseline " + record.filename() + " under " + baselineDir, e);
        }
    }
}
