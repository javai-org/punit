package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmpiricalChecks")
class EmpiricalChecksTest {

    @Test
    @DisplayName("sampleSizeConstraint returns empty when test ≤ baseline")
    void sampleSizeWithinBound() {
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 100, 100, Map.of())).isEmpty();
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 50, 100, Map.of())).isEmpty();
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 0, 100, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("sampleSizeConstraint returns INCONCLUSIVE when test > baseline")
    void sampleSizeExceeded() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "bernoulli-pass-rate", 200, 100, Map.of());

        assertThat(result).isPresent();
        CriterionResult r = result.get();
        assertThat(r.criterionName()).isEqualTo("bernoulli-pass-rate");
        assertThat(r.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(r.explanation())
                .contains("test sample size (200)")
                .contains("baseline sample size (100)")
                .contains("at least as rigorous");
        assertThat(r.detail()).containsEntry("testSampleCount", 200);
        assertThat(r.detail()).containsEntry("baselineSampleCount", 100);
        assertThat(r.detail()).containsEntry("origin", "EMPIRICAL");
    }

    @Test
    @DisplayName("additionalDetail entries are merged into the violation's detail map")
    void additionalDetailMerged() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "percentile-latency", 1000, 100,
                Map.of("assertedPercentiles", "p95,p99"));

        assertThat(result).isPresent();
        Map<String, Object> detail = result.get().detail();
        assertThat(detail).containsEntry("assertedPercentiles", "p95,p99");
        assertThat(detail).containsEntry("testSampleCount", 1000);
        assertThat(detail).containsEntry("baselineSampleCount", 100);
        assertThat(detail).containsEntry("origin", "EMPIRICAL");
    }

    @Test
    @DisplayName("additionalDetail's origin is preserved if the caller already set it")
    void additionalDetailOriginIsPreserved() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "c", 200, 100, Map.of("origin", "CUSTOM"));

        assertThat(result.get().detail()).containsEntry("origin", "CUSTOM");
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.sampleSizeConstraint(null, 1, 1, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.sampleSizeConstraint("c", 1, 1, null));
    }
}
