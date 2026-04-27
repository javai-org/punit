package org.javai.punit.engine.baseline;

import static org.javai.punit.engine.baseline.BaselineSchema.CRITERION_BERNOULLI_PASS_RATE;
import static org.javai.punit.engine.baseline.BaselineSchema.CRITERION_PERCENTILE_LATENCY;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_FACTORS_FINGERPRINT;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_GENERATED_AT;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_INPUTS_IDENTITY;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_METHOD_NAME;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_OBSERVED_PASS_RATE;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_PERCENTILES;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_SAMPLE_COUNT;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_SCHEMA_VERSION;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_STATISTICS;
import static org.javai.punit.engine.baseline.BaselineSchema.FIELD_USE_CASE_ID;
import static org.javai.punit.engine.baseline.BaselineSchema.PERCENTILE_KEY_P50;
import static org.javai.punit.engine.baseline.BaselineSchema.PERCENTILE_KEY_P90;
import static org.javai.punit.engine.baseline.BaselineSchema.PERCENTILE_KEY_P95;
import static org.javai.punit.engine.baseline.BaselineSchema.PERCENTILE_KEY_P99;
import static org.javai.punit.engine.baseline.BaselineSchema.SCHEMA_VERSION_VALUE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.LatencyStatistics;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises a {@link BaselineRecord} to the
 * {@code punit-baseline-2} YAML schema and writes it to disk.
 *
 * <p>Filename is derived from the record itself
 * ({@link BaselineRecord#filename()}). The writer creates the parent
 * directory if it does not exist.
 *
 * <p>Two unsupported {@link BaselineStatistics} flavours are rejected
 * at write time with an {@link IllegalStateException}: the writer's
 * job is to produce a file the reader can fully parse, so silently
 * dropping unknown statistics kinds would create asymmetry. New
 * statistics kinds extend {@link #serialiseStatisticsEntry} alongside
 * a matching read path in {@link BaselineReader}.
 */
public final class BaselineWriter {

    /**
     * Writes {@code record} to {@code baselineDir} under its canonical
     * filename. Returns the path the file was written to.
     */
    public Path write(BaselineRecord record, Path baselineDir) throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(baselineDir, "baselineDir");
        Files.createDirectories(baselineDir);
        Path file = baselineDir.resolve(record.filename());
        Files.writeString(file, toYaml(record), StandardCharsets.UTF_8);
        return file;
    }

    /** Serialises {@code record} to the YAML schema as a string. */
    public String toYaml(BaselineRecord record) {
        Objects.requireNonNull(record, "record");
        return yaml().dump(toYamlMap(record));
    }

    private Map<String, Object> toYamlMap(BaselineRecord record) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION_VALUE);
        root.put(FIELD_USE_CASE_ID, record.useCaseId());
        root.put(FIELD_METHOD_NAME, record.methodName());
        root.put(FIELD_FACTORS_FINGERPRINT, record.factorsFingerprint());
        root.put(FIELD_INPUTS_IDENTITY, record.inputsIdentity());
        root.put(FIELD_SAMPLE_COUNT, record.sampleCount());
        root.put(FIELD_GENERATED_AT, DateTimeFormatter.ISO_INSTANT.format(record.generatedAt()));

        Map<String, Object> stats = new LinkedHashMap<>();
        record.statisticsByCriterionName().forEach((name, value) ->
                stats.put(name, serialiseStatisticsEntry(name, value)));
        root.put(FIELD_STATISTICS, stats);
        return root;
    }

    private Map<String, Object> serialiseStatisticsEntry(
            String criterionName, BaselineStatistics statistics) {
        if (statistics instanceof PassRateStatistics passRate) {
            return serialisePassRate(passRate);
        }
        if (statistics instanceof LatencyStatistics latency) {
            return serialiseLatency(latency);
        }
        throw new IllegalStateException(
                "Unsupported baseline statistics kind for criterion '" + criterionName
                        + "': " + statistics.getClass().getName()
                        + " — extend BaselineWriter and BaselineReader together to add support.");
    }

    private Map<String, Object> serialisePassRate(PassRateStatistics stats) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(FIELD_OBSERVED_PASS_RATE, stats.observedPassRate());
        entry.put(FIELD_SAMPLE_COUNT, stats.sampleCount());
        return entry;
    }

    private Map<String, Object> serialiseLatency(LatencyStatistics stats) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(FIELD_SAMPLE_COUNT, stats.sampleCount());

        LatencyResult percentiles = stats.percentiles();
        Map<String, Object> percentileMap = new LinkedHashMap<>();
        percentileMap.put(PERCENTILE_KEY_P50, isoDuration(percentiles.p50()));
        percentileMap.put(PERCENTILE_KEY_P90, isoDuration(percentiles.p90()));
        percentileMap.put(PERCENTILE_KEY_P95, isoDuration(percentiles.p95()));
        percentileMap.put(PERCENTILE_KEY_P99, isoDuration(percentiles.p99()));
        entry.put(FIELD_PERCENTILES, percentileMap);
        return entry;
    }

    private static String isoDuration(Duration duration) {
        return duration.toString();
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    /**
     * The criterion-name keys this writer recognises. Useful for
     * tests and for callers that build {@link BaselineRecord}
     * instances programmatically.
     */
    public static final class CriterionNames {
        private CriterionNames() { }
        public static final String BERNOULLI_PASS_RATE = CRITERION_BERNOULLI_PASS_RATE;
        public static final String PERCENTILE_LATENCY = CRITERION_PERCENTILE_LATENCY;
    }
}
