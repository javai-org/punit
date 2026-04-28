package org.javai.punit.engine.baseline;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.javai.punit.api.typed.covariate.CovariateProfile;
import org.javai.punit.api.typed.spec.BaselineStatistics;

/**
 * In-memory shape of a baseline file. Carries the identity keys the
 * {@link BaselineResolver} matches against, the
 * sampling-population integrity fields the empirical criteria
 * cross-check, and a per-criterion-name map of
 * {@link BaselineStatistics} flavours.
 *
 * <p>Mirrors the schema documented in
 * {@code docs/DES-BASELINE-YAML-SCHEMA.md}.
 *
 * @param useCaseId           the use case's stable identity, as
 *                            returned by {@code UseCase.id()}
 * @param methodName          the spec-method name that produced
 *                            this baseline
 * @param factorsFingerprint  short hash of the factor values the
 *                            measure ran under
 * @param inputsIdentity      full SHA-256 inputs identity, as
 *                            returned by {@code Sampling.inputsIdentity()}
 * @param sampleCount         the total number of samples the
 *                            measure executed
 * @param generatedAt         when the measure produced this baseline
 * @param statisticsByCriterionName  one
 *        {@link BaselineStatistics} entry per criterion, keyed by
 *        {@code Criterion.name()}
 * @param covariateProfile    the resolved covariate profile under
 *        which this baseline was measured. Empty when the use case
 *        declared no covariates (legacy / covariate-insensitive
 *        baselines). Part of the baseline's identity per UC04 — a
 *        baseline measured under one profile must not silently match
 *        a test running under a different profile.
 */
public record BaselineRecord(
        String useCaseId,
        String methodName,
        String factorsFingerprint,
        String inputsIdentity,
        int sampleCount,
        Instant generatedAt,
        Map<String, BaselineStatistics> statisticsByCriterionName,
        CovariateProfile covariateProfile) {

    public BaselineRecord {
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        Objects.requireNonNull(inputsIdentity, "inputsIdentity");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(statisticsByCriterionName, "statisticsByCriterionName");
        Objects.requireNonNull(covariateProfile, "covariateProfile");
        if (useCaseId.isBlank()) {
            throw new IllegalArgumentException("useCaseId must not be blank");
        }
        if (methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (factorsFingerprint.isBlank()) {
            throw new IllegalArgumentException("factorsFingerprint must not be blank");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be non-negative, got " + sampleCount);
        }
        if (statisticsByCriterionName.isEmpty()) {
            throw new IllegalArgumentException(
                    "statisticsByCriterionName must not be empty — a baseline with no "
                            + "criterion entries is not consumable by any empirical criterion");
        }
        statisticsByCriterionName = Map.copyOf(new LinkedHashMap<>(statisticsByCriterionName));
    }

    /**
     * Convenience constructor for callers that don't carry a covariate
     * profile (legacy baselines, tests that don't exercise covariate
     * resolution). Equivalent to the canonical constructor with
     * {@link CovariateProfile#empty()}.
     */
    public BaselineRecord(
            String useCaseId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName) {
        this(useCaseId, methodName, factorsFingerprint, inputsIdentity,
                sampleCount, generatedAt, statisticsByCriterionName,
                CovariateProfile.empty());
    }

    /**
     * @return the canonical filename for this baseline, in the form
     *         {@code {useCaseId}.{methodName}-{factorsFingerprint}.yaml}.
     */
    public String filename() {
        return useCaseId + "." + methodName + "-" + factorsFingerprint + ".yaml";
    }
}
