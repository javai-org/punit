package org.javai.punit.examples2.usecases;

import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseContract;
import org.javai.punit.examples2.infrastructure.payment.MockPaymentGateway;
import org.javai.punit.examples2.infrastructure.payment.PaymentGateway;
import org.javai.punit.examples2.infrastructure.payment.PaymentResult;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseOutcome;
import org.javai.punit.model.UseCaseResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Use case for processing payment transactions through a payment gateway.
 *
 * <p>This use case demonstrates the <b>SLA approach</b> to probabilistic testing,
 * where thresholds come from contractual agreements rather than empirical baselines.
 *
 * <h2>SLA Context</h2>
 * <p>Payment gateways typically have contractual SLAs specifying availability targets.
 * For example:
 * <ul>
 *   <li>"Payment Provider SLA v2.3, Section 4.1: 99.99% availability"</li>
 * </ul>
 *
 * <p>Unlike the shopping basket use case (where we discover acceptable failure rates
 * through measurement), here we <b>know</b> the acceptable failure rate upfront from
 * the contract. Tests verify that the gateway meets its contractual obligations.
 *
 * <h2>Covariates</h2>
 * <p>This use case tracks covariates that may affect gateway performance:
 * <ul>
 *   <li>{@code WEEKDAY_VERSUS_WEEKEND} - Temporal context (TEMPORAL)</li>
 *   <li>{@code gateway_region} - Payment provider region (OPERATIONAL)</li>
 * </ul>
 *
 * @see org.javai.punit.examples2.tests.PaymentGatewaySlaTest
 */
@UseCase(
        description = "Process payment transactions through a payment gateway",
        covariates = {StandardCovariate.WEEKDAY_VERSUS_WEEKEND},
        categorizedCovariates = {
                @Covariate(key = "gateway_region", category = CovariateCategory.OPERATIONAL)
        }
)
public class PaymentGatewayUseCase implements UseCaseContract {

    private final PaymentGateway gateway;
    private String region = "us-east-1";

    /**
     * Creates a use case with the default mock gateway.
     */
    public PaymentGatewayUseCase() {
        this(MockPaymentGateway.instance());
    }

    /**
     * Creates a use case with a specific gateway implementation.
     *
     * @param gateway the payment gateway to use
     */
    public PaymentGatewayUseCase(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COVARIATE SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    @CovariateSource("gateway_region")
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes a payment charge.
     *
     * @param cardToken the tokenized card reference
     * @param amountCents the amount to charge in cents
     * @return outcome containing result and success criteria
     */
    public UseCaseOutcome chargeCard(String cardToken, long amountCents) {
        Instant start = Instant.now();

        PaymentResult paymentResult = gateway.charge(cardToken, amountCents);

        Duration executionTime = Duration.between(start, Instant.now());

        UseCaseResult result = UseCaseResult.builder()
                .value("success", paymentResult.success())
                .value("transactionId", paymentResult.transactionId())
                .value("errorCode", paymentResult.errorCode())
                .meta("cardToken", cardToken)
                .meta("amountCents", amountCents)
                .meta("region", region)
                .executionTime(executionTime)
                .build();

        // Simple success criterion: the transaction succeeded
        UseCaseCriteria criteria = UseCaseCriteria.ordered()
                .criterion("Transaction succeeded",
                        () -> result.getBoolean("success", false))
                .build();

        return new UseCaseOutcome(result, criteria);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTOR SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Standard payment amounts for testing.
     *
     * <p>Covers a range of typical transaction values.
     *
     * @return factor arguments with various payment amounts
     */
    public static List<FactorArguments> standardAmounts() {
        return FactorArguments.configurations()
                .names("cardToken", "amountCents")
                .values("tok_visa_4242", 1999L)       // $19.99
                .values("tok_visa_4242", 4999L)       // $49.99
                .values("tok_visa_4242", 9999L)       // $99.99
                .values("tok_mastercard_5555", 2499L) // $24.99
                .values("tok_mastercard_5555", 14999L)// $149.99
                .values("tok_amex_3782", 29999L)      // $299.99
                .stream().toList();
    }

    /**
     * Single payment for focused testing.
     *
     * @return a single factor argument
     */
    public static List<FactorArguments> singlePayment() {
        return FactorArguments.configurations()
                .names("cardToken", "amountCents")
                .values("tok_visa_4242", 1999L)
                .stream().toList();
    }
}
