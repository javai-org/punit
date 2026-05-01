package org.javai.punit.api.spec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.Sampling;
import org.javai.punit.api.ThresholdOrigin;

/**
 * Shared evaluate-time integrity rules for empirical criteria.
 *
 * <p>Lives outside any single criterion so the same constraint is
 * applied uniformly to every empirical comparison. Each rule
 * returns {@code Optional<CriterionResult>} — present when the
 * rule is violated (the caller returns it directly as the
 * criterion's verdict), empty when the comparison may proceed.
 *
 * <p>Today this carries the sample-size rule. Future empirical
 * integrity checks (factor compatibility, supplier-identity
 * match per {@code DES-INPUTS-IDENTITY-SUPPLIER.md}, etc.) land
 * here as additional rules so every empirical criterion in punit
 * applies them by referencing one helper rather than inlining the
 * logic.
 */
public final class EmpiricalChecks {

    private EmpiricalChecks() {}

    /**
     * Sample-size rule: the test's sample count must not exceed the
     * resolved baseline's sample count. In punit's authoring model
     * the baseline is the rigorous-truth measurement; the test is a
     * sentinel against it. A test sample count that exceeds the
     * baseline's inverts the precision relationship the model
     * assumes (the test would be claiming tighter confidence than
     * the baseline it grounds), so the framework rejects the
     * comparison rather than emit a verdict against a baseline the
     * test out-rigours.
     *
     * <p>Returns {@link Verdict#INCONCLUSIVE} rather than
     * {@link Verdict#FAIL}: this is configuration error, not
     * service degradation. The diagnostic names both counts and
     * the corrective action.
     *
     * @param criterionName       the criterion's stable name
     *                            ({@link Criterion#name()}) — used in the
     *                            returned {@code CriterionResult}
     * @param testSampleCount     the count of samples taken by the test
     * @param baselineSampleCount the count of samples in the resolved baseline
     * @param additionalDetail    criterion-specific entries to include in
     *                            the violation's detail map (e.g.
     *                            {@code confidence}, {@code assertedPercentiles}).
     *                            May be empty.
     * @return an INCONCLUSIVE {@code CriterionResult} when
     *         {@code testSampleCount > baselineSampleCount}; empty
     *         otherwise.
     */
    public static Optional<CriterionResult> sampleSizeConstraint(
            String criterionName,
            int testSampleCount,
            int baselineSampleCount,
            Map<String, Object> additionalDetail) {
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(additionalDetail, "additionalDetail");
        if (testSampleCount <= baselineSampleCount) {
            return Optional.empty();
        }
        Map<String, Object> detail = new LinkedHashMap<>(additionalDetail);
        detail.putIfAbsent("origin", ThresholdOrigin.EMPIRICAL.name());
        detail.put("testSampleCount", testSampleCount);
        detail.put("baselineSampleCount", baselineSampleCount);
        String reason = "test sample size (" + testSampleCount + ") exceeds baseline "
                + "sample size (" + baselineSampleCount + "). The baseline must be "
                + "at least as rigorous as the test it grounds. Re-run the baseline "
                + "measure with a larger sample size, or reduce the test's samples "
                + "to ≤ " + baselineSampleCount + ".";
        return Optional.of(new CriterionResult(
                criterionName, Verdict.INCONCLUSIVE, reason, detail));
    }

    /**
     * Inputs-identity rule: the test's inputs identity must equal the
     * resolved baseline's recorded inputs identity. The two are the
     * cross-process restatement of the same in-process integrity
     * guarantee that the shared {@link Sampling}
     * value gives a measure / probabilistic-test pair.
     *
     * <p>An empirical comparison is statistically meaningful only when
     * the test and the baseline draw from the same sampling population.
     * Same use case + same factors is one half; same input list — by
     * content, not by reference, since the two processes don't share
     * memory — is the other. {@code Sampling.inputsIdentity()} is the
     * SHA-256 content hash that captures the inputs half (per
     * {@code docs/DES-INPUTS-IDENTITY-SUPPLIER.md}). The framework
     * records it on every baseline at write time and compares here.
     *
     * <p>Returns {@link Verdict#INCONCLUSIVE} rather than
     * {@link Verdict#FAIL}: a mismatch is configuration drift, not
     * service degradation. The diagnostic names both identities
     * (truncated for readability) and the corrective action.
     *
     * @param criterionName     the criterion's stable name
     *                          ({@link Criterion#name()}) — used in the
     *                          returned {@code CriterionResult}
     * @param testIdentity      the test's
     *                          {@code Sampling.inputsIdentity()}
     * @param baselineIdentity  the inputs identity recorded by the
     *                          resolved baseline
     * @param additionalDetail  criterion-specific entries to include in
     *                          the violation's detail map. May be empty.
     * @return an INCONCLUSIVE {@code CriterionResult} when the two
     *         identities differ; empty when they match
     */
    public static Optional<CriterionResult> inputsIdentityMatch(
            String criterionName,
            String testIdentity,
            String baselineIdentity,
            Map<String, Object> additionalDetail) {
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(testIdentity, "testIdentity");
        Objects.requireNonNull(baselineIdentity, "baselineIdentity");
        Objects.requireNonNull(additionalDetail, "additionalDetail");
        if (testIdentity.equals(baselineIdentity)) {
            return Optional.empty();
        }
        Map<String, Object> detail = new LinkedHashMap<>(additionalDetail);
        detail.putIfAbsent("origin", ThresholdOrigin.EMPIRICAL.name());
        detail.put("testInputsIdentity", testIdentity);
        detail.put("baselineInputsIdentity", baselineIdentity);
        String reason = "test inputs identity (" + truncate(testIdentity)
                + ") differs from the resolved baseline's recorded inputs "
                + "identity (" + truncate(baselineIdentity) + "). An empirical "
                + "comparison requires both sides to draw from the same input "
                + "population — re-run the baseline measure with the test's "
                + "inputs, or pin the test to the baseline that was measured "
                + "with these inputs.";
        return Optional.of(new CriterionResult(
                criterionName, Verdict.INCONCLUSIVE, reason, detail));
    }

    /**
     * Truncates a {@code sha256:HEX...} identity string to a short
     * prefix suitable for embedding in a diagnostic message. Other
     * identity formats are returned as-is.
     */
    private static String truncate(String identity) {
        if (identity.startsWith("sha256:") && identity.length() > 7 + 12) {
            return identity.substring(0, 7 + 12) + "…";
        }
        return identity;
    }
}
