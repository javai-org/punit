package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EarlyTerminationMessages}.
 */
class EarlyTerminationMessagesTest {

    @Test
    void buildImpossibilityExplanationIncludesDetails() {
        // 95% of 100 = 95 required; 0 successes after 6 samples, 94 remaining
        String explanation = EarlyTerminationMessages.buildImpossibilityExplanation(0, 6, 100, 95);

        assertThat(explanation)
                .contains("6 samples")
                .contains("0 successes")
                .contains("94")  // remaining
                .contains("95"); // required
    }

    @Test
    void buildSuccessGuaranteedExplanationIncludesDetails() {
        // 80% of 10 = 8 required; 8 successes after 8 samples, 2 remaining
        String explanation = EarlyTerminationMessages.buildSuccessGuaranteedExplanation(8, 8, 10, 8);

        assertThat(explanation)
                .contains("8 samples")
                .contains("8 successes")
                .contains("1.0000")  // pass rate
                .contains("2 remaining"); // skipped samples
    }

    @Test
    void buildExplanationDispatchesToCorrectMethod() {
        // 80% of 10 = 8 required
        String impossibilityExplanation = EarlyTerminationMessages.buildExplanation(
                TerminationReason.IMPOSSIBILITY, 0, 3, 10, 8);
        assertThat(impossibilityExplanation).contains("maximum possible successes");

        String successExplanation = EarlyTerminationMessages.buildExplanation(
                TerminationReason.SUCCESS_GUARANTEED, 8, 8, 10, 8);
        assertThat(successExplanation).contains("already met");
    }

    @Test
    void buildExplanationFallsBackToDescriptionForOtherReasons() {
        String explanation = EarlyTerminationMessages.buildExplanation(
                TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED, 5, 10, 20, 16);
        assertThat(explanation).isEqualTo(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED.getDescription());
    }
}
