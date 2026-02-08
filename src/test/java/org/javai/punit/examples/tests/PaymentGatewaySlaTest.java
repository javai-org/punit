package org.javai.punit.examples.tests;

import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.PaymentGatewayUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Smoke tests for payment gateway reliability.
 *
 * <p>These are <b>conformance smoke tests</b>: quick sanity checks that catch
 * catastrophic failures against a prescribed threshold. They are not sized for
 * compliance verification — at 50–200 samples against a 99.99% target, even
 * perfect results cannot provide compliance-grade statistical evidence.
 * PUnit flags this explicitly in the verdict.
 *
 * <p>Compliance testing does not even make sense here. The payment gateway is
 * a third-party service we do not control. We cannot make it comply with
 * anything — we can only observe its behaviour. What these smoke tests give
 * us is early warning: if the gateway starts failing catastrophically, we
 * detect it quickly. If we need to hold the vendor accountable against their
 * SLA, the evidence comes from production monitoring at scale, not from a
 * test suite running a few hundred samples.
 *
 * <p>The {@code thresholdOrigin} and {@code contractRef} annotations record
 * where the threshold came from (provenance metadata for audit traceability),
 * but do not change the character of the test.
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
     * Smoke test with transparent stats output.
     *
     * <p>The transparent stats verdict will include a caveat noting that
     * the sample is not sized for compliance verification.
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 200,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1",
            transparentStats = true
    )
    void smokeTestWithTransparentStats(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_visa_4242", 1999L).assertAll();
    }

    /**
     * Fast smoke test for CI pipelines.
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 50,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1"
    )
    void smokeTestQuick(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_visa_4242", 1999L).assertAll();
    }

    /**
     * Smoke test against an internal SLO (99.9%, more relaxed than the 99.99% SLA).
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 100,
            minPassRate = 0.999,
            thresholdOrigin = ThresholdOrigin.SLO,
            contractRef = "Internal Payment SLO - Q4 2024"
    )
    void smokeTestAgainstSlo(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_mastercard_5555", 2499L).assertAll();
    }

    /**
     * Smoke test with a high-value transaction.
     */
    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 100,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1"
    )
    void smokeTestHighValue(PaymentGatewayUseCase useCase) {
        useCase.chargeCard("tok_amex_3782", 29999L).assertAll();
    }
}
