package org.javai.punit.engine.baseline;

/**
 * Schema constants shared by the baseline writer and reader.
 *
 * <p>See {@code docs/DES-BASELINE-YAML-SCHEMA.md} for the full
 * specification. Keeping the field names and the discriminator value
 * in one place avoids a drift class — the writer and reader either
 * agree, or the build breaks.
 */
final class BaselineSchema {

    private BaselineSchema() { }

    /** The structural-version discriminator for the baseline YAML schema. */
    static final String SCHEMA_VERSION_VALUE = "punit-baseline-2";

    static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    static final String FIELD_USE_CASE_ID = "useCaseId";
    static final String FIELD_METHOD_NAME = "methodName";
    static final String FIELD_FACTORS_FINGERPRINT = "factorsFingerprint";
    static final String FIELD_INPUTS_IDENTITY = "inputsIdentity";
    static final String FIELD_SAMPLE_COUNT = "sampleCount";
    static final String FIELD_GENERATED_AT = "generatedAt";
    static final String FIELD_STATISTICS = "statistics";
    static final String FIELD_COVARIATES = "covariates";

    // BernoulliPassRate / PassRateStatistics
    static final String FIELD_OBSERVED_PASS_RATE = "observedPassRate";

    // PercentileLatency / LatencyStatistics
    static final String FIELD_PERCENTILES = "percentiles";
    static final String PERCENTILE_KEY_P50 = "p50";
    static final String PERCENTILE_KEY_P90 = "p90";
    static final String PERCENTILE_KEY_P95 = "p95";
    static final String PERCENTILE_KEY_P99 = "p99";

    // Criterion-name keys (mirror Criterion.name() values).
    static final String CRITERION_BERNOULLI_PASS_RATE = "bernoulli-pass-rate";
    static final String CRITERION_PERCENTILE_LATENCY = "percentile-latency";
}
