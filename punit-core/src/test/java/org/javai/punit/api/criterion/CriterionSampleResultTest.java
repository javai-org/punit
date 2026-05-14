package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionResult;
import org.junit.jupiter.api.Test;

/**
 * Per-outcome invariants on the {@link CriterionSampleResult}
 * record. The canonical constructor enforces the shape contracts
 * each of the three outcomes carries.
 */
class CriterionSampleResultTest {

    @Test
    void passCarriesPostconditionResultsAndNoTransformFailure() {
        var pr = PostconditionResult.passed("clause");
        var r = CriterionSampleResult.pass("id", List.of(pr));

        assertThat(r.outcome()).isEqualTo(CriterionSampleOutcome.PASS);
        assertThat(r.postconditionResults()).containsExactly(pr);
        assertThat(r.transformFailure()).isEmpty();
    }

    @Test
    void failCarriesPostconditionResultsAndNoTransformFailure() {
        var pr = PostconditionResult.failed("clause", "reason");
        var r = CriterionSampleResult.fail("id", List.of(pr));

        assertThat(r.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
        assertThat(r.postconditionResults()).containsExactly(pr);
        assertThat(r.transformFailure()).isEmpty();
    }

    @Test
    void inconclusiveCarriesTransformFailureAndEmptyPostconditionResults() {
        Outcome.Fail<?> tf = (Outcome.Fail<?>) Outcome.fail("err", "msg");
        var r = CriterionSampleResult.inconclusive("id", tf);

        assertThat(r.outcome()).isEqualTo(CriterionSampleOutcome.INCONCLUSIVE);
        assertThat(r.postconditionResults()).isEmpty();
        assertThat(r.transformFailure()).contains(tf);
    }

    @Test
    void inconclusiveWithoutTransformFailureRejected() {
        assertThatThrownBy(() -> new CriterionSampleResult(
                "id",
                CriterionSampleOutcome.INCONCLUSIVE,
                List.of(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transformFailure");
    }

    @Test
    void inconclusiveWithPostconditionResultsRejected() {
        Outcome.Fail<?> tf = (Outcome.Fail<?>) Outcome.fail("err", "msg");
        var pr = PostconditionResult.passed("c");

        assertThatThrownBy(() -> new CriterionSampleResult(
                "id",
                CriterionSampleOutcome.INCONCLUSIVE,
                List.of(pr),
                Optional.of(tf)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postcondition");
    }

    @Test
    void nonInconclusiveWithTransformFailureRejected() {
        Outcome.Fail<?> tf = (Outcome.Fail<?>) Outcome.fail("err", "msg");

        assertThatThrownBy(() -> new CriterionSampleResult(
                "id",
                CriterionSampleOutcome.PASS,
                List.of(),
                Optional.of(tf)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transformFailure");
    }
}
