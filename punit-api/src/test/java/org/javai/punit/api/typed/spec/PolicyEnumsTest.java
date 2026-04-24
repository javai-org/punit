package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Stage 3 policy enums")
class PolicyEnumsTest {

    @Test
    @DisplayName("BudgetExhaustionPolicy carries exactly PASS_INCOMPLETE and FAIL")
    void budgetPolicyShape() {
        assertThat(BudgetExhaustionPolicy.values())
                .containsExactly(BudgetExhaustionPolicy.PASS_INCOMPLETE, BudgetExhaustionPolicy.FAIL);
    }

    @Test
    @DisplayName("ExceptionPolicy carries exactly ABORT_TEST and FAIL_SAMPLE")
    void exceptionPolicyShape() {
        assertThat(ExceptionPolicy.values())
                .containsExactly(ExceptionPolicy.ABORT_TEST, ExceptionPolicy.FAIL_SAMPLE);
    }

    @Test
    @DisplayName("VerdictDimension carries exactly FUNCTIONAL, LATENCY, BOTH")
    void dimensionShape() {
        assertThat(VerdictDimension.values())
                .containsExactly(VerdictDimension.FUNCTIONAL, VerdictDimension.LATENCY, VerdictDimension.BOTH);
    }
}
