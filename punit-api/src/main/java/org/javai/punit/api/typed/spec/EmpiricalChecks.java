package org.javai.punit.api.typed.spec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
}
