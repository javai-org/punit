package org.javai.punit.examples.probabilistictests;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.PaymentGatewayUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Probabilistic tests for payment gateway reliability, demonstrating how
 * {@link TestIntent} interacts with sample sizing and threshold targets.
 *
 * <p>The tests in this class illustrate the two intent modes:
 * <ul>
 *   <li><b>VERIFICATION</b> — the sample size is sufficient for the target
 *       pass rate, so PUnit can provide statistical evidence that the SUT
 *       meets the threshold. This is the default intent and requires a
 *       feasible sample size; PUnit will reject the test configuration if
 *       the sample is too small.</li>
 *   <li><b>SMOKE</b> — the sample size is intentionally undersized relative
 *       to the target. The test acts as a sentinel: it catches catastrophic
 *       regressions quickly but does not claim statistical verification.
 *       PUnit notes the sizing gap in the verdict.</li>
 * </ul>
 *
 * <p>The {@code thresholdOrigin} and {@code contractRef} annotations record
 * provenance — where the threshold came from — for audit traceability.
 * They do not affect test execution or verdict logic.
 *
 * @see PaymentGatewayUseCase
 */
@Disabled("Example test - run manually")
public class PaymentGatewaySlaTest {

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void setUp() {
        provider.register(PaymentGatewayUseCase.class, PaymentGatewayUseCase::new);
    }

    /**
     * Test against an internal SLO (99% more relaxed than the 99.99% SLA).
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 268, // minimal sample size required to verify SLO
            minPassRate = 0.99, // medium reliability service
            intent = TestIntent.VERIFICATION, // intent is verification by default (can be omitted)
            thresholdOrigin = ThresholdOrigin.SLO,
            contractRef = "Internal Payment SLO - Q4 2024"
    )
    void testInternalSlo(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_mastercard_5555", 2499L).assertAll();
    }

    /**
     * Fast smoke test for CI pipelines.
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 50, // sample size is clearly not suited to validation
            minPassRate = 0.9999, // high-reliability service
            intent = TestIntent.SMOKE, // intent therefore explicitly set to smoke
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1"
    )
    void smokeTestSla(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_visa_4242", 1999L).assertAll();
    }

    /**
     * Smoke test with transparent stats output.
     *
     * <p>The transparent stats verdict will include a caveat noting that
     * the sample is not sized for compliance verification.
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 50,
            minPassRate = 0.9999,
            intent = TestIntent.SMOKE,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1",
            transparentStats = true // more detailed report
    )
    void smokeTestWithTransparentStats(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_visa_4242", 1999L).assertAll();
    }

}
