package org.javai.punit.examples.tests;

import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.usecases.PaymentGatewayUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * SLA-driven probabilistic test for payment gateway reliability.
 *
 * <p>This test demonstrates the <b>SLA approach</b> to probabilistic testing,
 * where thresholds come from contractual agreements rather than empirical
 * baselines.
 *
 * <h2>SLA vs Empirical Approach</h2>
 *
 * <h3>Empirical (ShoppingBasketTest)</h3>
 * <ul>
 *   <li>Run MEASURE experiment to establish baseline</li>
 *   <li>Derive threshold from observed behavior</li>
 *   <li>Test verifies "no regression from baseline"</li>
 *   <li>Threshold may change over time as system evolves</li>
 * </ul>
 *
 * <h3>SLA (This Test)</h3>
 * <ul>
 *   <li>Threshold from external contract (SLA document)</li>
 *   <li>No baseline measurement needed</li>
 *   <li>Test verifies "meets contractual obligation"</li>
 *   <li>Threshold is fixed by agreement, not measurement</li>
 * </ul>
 *
 * <h2>What This Demonstrates</h2>
 * <ul>
 *   <li>{@code thresholdOrigin = ThresholdOrigin.SLA} - Document threshold origin</li>
 *   <li>{@code contractRef} - Reference to SLA document</li>
 *   <li>{@code minPassRate = 0.9999} - Hard-coded 99.99% threshold</li>
 *   <li>High sample count for detecting small deviations</li>
 * </ul>
 *
 * <h2>Statistical Significance for High Thresholds</h2>
 * <p>When testing for 99.99% reliability:
 * <ul>
 *   <li>1,000 samples: Can detect ~1% deviation</li>
 *   <li>10,000 samples: Can detect ~0.1% deviation</li>
 *   <li>100,000 samples: Can detect ~0.03% deviation</li>
 * </ul>
 *
 * <p>The mock gateway has ~99.97% actual reliability (0.03% failure rate),
 * intentionally below the 99.99% SLA. With enough samples, this gap is
 * detectable.
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew test --tests "PaymentGatewaySlaTest"
 * }</pre>
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
     * Tests payment gateway against 99.99% SLA target.
     *
     * <p>This test:
     * <ul>
     *   <li>Uses hard-coded 99.99% threshold from SLA</li>
     *   <li>Runs 10,000 samples for statistical power</li>
     *   <li>Documents the SLA reference for audit purposes</li>
     * </ul>
     *
     * <p>Note: This test is expected to fail periodically because the mock
     * gateway has ~99.97% actual reliability, slightly below the 99.99% SLA.
     * This demonstrates the purpose of SLA testing - detecting when actual
     * performance doesn't meet contractual obligations.
     *
     * @param useCase the use case instance
     * @param cardToken the card token
     * @param amountCents the amount to charge
     */

    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 10000,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1",
            transparentStats = true
    )
    @FactorSource(value = "standardAmounts", factors = {"cardToken", "amountCents"})
    void testSlaCompliance(
            PaymentGatewayUseCase useCase,
            @Factor("cardToken") String cardToken,
            @Factor("amountCents") Long amountCents
    ) {
        useCase.chargeCard(cardToken, amountCents).assertAll();
    }

    /**
     * Tests with fewer samples for faster feedback (less precise).
     *
     * <p>With 1,000 samples, the test can detect larger deviations (~1%)
     * but may not detect the small gap between 99.99% and 99.97%.
     *
     * @param useCase the use case instance
     * @param cardToken the card token
     * @param amountCents the amount to charge
     */

    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 1000,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1"
    )
    @FactorSource(value = "standardAmounts", factors = {"cardToken", "amountCents"})
    void testSlaComplianceQuick(
            PaymentGatewayUseCase useCase,
            @Factor("cardToken") String cardToken,
            @Factor("amountCents") Long amountCents
    ) {
        useCase.chargeCard(cardToken, amountCents).assertAll();
    }

    /**
     * Tests with a more relaxed internal SLO.
     *
     * <p>Organizations often have internal SLOs that are more relaxed
     * than external SLAs. This test uses 99.9% (vs 99.99% SLA).
     *
     * @param useCase the use case instance
     * @param cardToken the card token
     * @param amountCents the amount to charge
     */

    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 1000,
            minPassRate = 0.999,
            thresholdOrigin = ThresholdOrigin.SLO,
            contractRef = "Internal Payment SLO - Q4 2024"
    )
    @FactorSource(value = "standardAmounts", factors = {"cardToken", "amountCents"})
    void testSloCompliance(
            PaymentGatewayUseCase useCase,
            @Factor("cardToken") String cardToken,
            @Factor("amountCents") Long amountCents
    ) {
        useCase.chargeCard(cardToken, amountCents).assertAll();
    }

    /**
     * Tests single payment type at high volume.
     *
     * <p>Using a single card/amount combination isolates payment type
     * variance from reliability measurement.
     *
     * @param useCase the use case instance
     * @param cardToken the card token
     * @param amountCents the amount to charge
     */

    @ProbabilisticTest(
            useCase = PaymentGatewayUseCase.class,
            samples = 5000,
            minPassRate = 0.9999,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Payment Provider SLA v2.3, Section 4.1"
    )
    @FactorSource(value = "singlePayment", factors = {"cardToken", "amountCents"})
    void testSlaComplianceSinglePaymentType(
            PaymentGatewayUseCase useCase,
            @Factor("cardToken") String cardToken,
            @Factor("amountCents") Long amountCents
    ) {
        useCase.chargeCard(cardToken, amountCents).assertAll();
    }
}
