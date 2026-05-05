package org.javai.punit.engine.optimize;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.spec.FactorsStepper.IterationResult;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.Trial;
import org.javai.punit.engine.output.ResultProjections;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises a completed OPTIMIZE run's history to the EX06 YAML
 * schema. Pure — performs no I/O. The
 * {@link org.javai.punit.runtime.OptimizeEmitter OPTIMIZE emitter}
 * orchestrates persistence (writing to disk or to an in-memory sink
 * for tests).
 *
 * <p>One file per optimize run, carrying the full iteration history
 * and a {@code convergence:} block. Filename is
 * {@code {useCaseId}/{experimentId}.yaml} — assembled by the
 * emitter, not the writer.
 */
public final class OptimizeOutputWriter {

    /** Schema-version value carried in every emitted file. */
    public static final String SCHEMA_VERSION = "punit-spec-1";

    /**
     * Build the EX06 YAML for one optimize run. Pure — no I/O.
     *
     * @param useCaseId the use case identifier
     * @param experimentId the experiment identifier (becomes the
     *                     filename stem)
     * @param objective {@code "MAXIMIZE"} or {@code "MINIMIZE"}
     * @param history the full iteration history in execution order;
     *                each {@link IterationResult} carries its
     *                factors, score, raw counts, and per-clause
     *                failure histogram.
     * @param bestIteration the iteration the optimisation chose as
     *                      best per the declared direction
     * @param terminationReason why the iteration loop stopped
     *                          ({@code MAX_ITERATIONS},
     *                          {@code NO_IMPROVEMENT},
     *                          {@code STEPPER_STOP}, or another
     *                          framework-recognised value)
     * @return YAML matching the EX06 canonical schema
     */
    public String writeYaml(
            String useCaseId,
            String experimentId,
            String objective,
            List<? extends IterationResult<?>> history,
            List<? extends SampleSummary<?>> iterationSummaries,
            IterationResult<?> bestIteration,
            String terminationReason) {

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("useCaseId", useCaseId);
        root.put("experimentId", experimentId);
        root.put("objective", objective);
        root.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        root.put("iterations", iterationsBlock(history, iterationSummaries));
        root.put("convergence", convergenceBlock(history, bestIteration, terminationReason));

        String dump = yaml().dump(root);
        return ResultProjections.injectAnchorComments(dump, allAnchors(iterationSummaries));
    }

    /**
     * Concatenate every iteration's per-trial anchors in iteration
     * order. The post-processor consumes them in document order as
     * it walks the dumped YAML's {@code sample[N]:} lines, so the
     * concatenated list must mirror that traversal: iteration[0]'s
     * trials, then iteration[1]'s trials, and so on.
     */
    private static List<String> allAnchors(List<? extends SampleSummary<?>> iterationSummaries) {
        List<String> all = new ArrayList<>();
        for (SampleSummary<?> summary : iterationSummaries) {
            all.addAll(ResultProjections.anchorsFor(summary.trials()));
        }
        return all;
    }

    private static List<Map<String, Object>> iterationsBlock(
            List<? extends IterationResult<?>> history,
            List<? extends SampleSummary<?>> iterationSummaries) {
        List<Map<String, Object>> out = new ArrayList<>(history.size());
        for (int idx = 0; idx < history.size(); idx++) {
            IterationResult<?> ir = history.get(idx);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("iteration", idx);
            entry.put("factors", factorsBlock(FactorBundle.of(ir.factors())));
            entry.put("score", ir.score());
            entry.put("successes", ir.successes());
            entry.put("failures", ir.failures());
            entry.put("samplesExecuted", ir.samplesExecuted());
            // Per-iteration result projection: one sample[N]: block
            // per trial, carrying input / postconditions / etc. The
            // writer leaves anchor-comment injection to the
            // top-level injectAnchorComments pass.
            if (idx < iterationSummaries.size()) {
                List<? extends Trial<?, ?>> trials = iterationSummaries.get(idx).trials();
                entry.put("resultProjection", ResultProjections.resultProjectionMap(trials));
            }
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> convergenceBlock(
            List<? extends IterationResult<?>> history,
            IterationResult<?> best,
            String terminationReason) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("totalIterations", history.size());
        int bestIndex = -1;
        if (best != null) {
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i) == best) {
                    bestIndex = i;
                    break;
                }
            }
            block.put("bestIteration", bestIndex);
            block.put("bestScore", best.score());
            block.put("bestFactors", factorsBlock(FactorBundle.of(best.factors())));
        }
        block.put("terminationReason", terminationReason);
        return block;
    }

    private static Map<String, Object> factorsBlock(FactorBundle bundle) {
        Map<String, Object> block = new LinkedHashMap<>();
        for (FactorBundle.Entry e : bundle.entries()) {
            block.put(e.name(), e.value().yamlValue());
        }
        return block;
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }
}
