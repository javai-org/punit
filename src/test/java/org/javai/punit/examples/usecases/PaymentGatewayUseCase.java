package org.javai.punit.examples.usecases;

import java.time.Duration;
import java.util.List;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorProvider;
import org.javai.punit.api.UseCase;
import org.javai.outcome.Outcome;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.examples.infrastructure.payment.MockPaymentGateway;
import org.javai.punit.examples.infrastructure.payment.PaymentGateway;
import org.javai.punit.examples.infrastructure.payment.PaymentResult;

/**
 * Use case for processing payment transactions through a payment gateway.
 *
 * <p>This use case demonstrates the <b>SLA approach</b> to probabilistic testing,
 * where thresholds come from contractual agreements rather than empirical baselines.
 *
 * <h2>SLA Context</h2>
 * <p>Payment gateways typically have contractual SLAs specifying both availability
 * and response time targets. For example:
 * <ul>
 *   <li>"Payment Provider SLA v2.3, Section 4.1: 99.99% availability"</li>
 *   <li>"Payment Provider SLA v2.3, Section 4.2: Transactions complete within 1 second"</li>
 * </ul>
 *
 * <p>Unlike the shopping basket use case (where we discover acceptable failure rates
 * through measurement), here we <b>know</b> the acceptable thresholds upfront from
 * the contract. Tests verify that the gateway meets its contractual obligations
 * for both correctness and timing.
 *
 * @see org.javai.punit.examples.tests.PaymentGatewaySlaTest
 */
@UseCase(description = "Process payment transactions through a payment gateway")
public class PaymentGatewayUseCase {

    /**
     * Input parameters for the payment service.
     *
     * @param cardToken the tokenized card reference
     * @param amountCents the amount to charge in cents
     */
    private record PaymentInput(String cardToken, long amountCents) {}

    /**
     * The service contract defining requirements for payment results.
     *
     * <p>This contract defines two requirements from the SLA:
     * <ul>
     *   <li>The transaction must succeed (correctness)</li>
     *   <li>The transaction must complete within 1 second (timing)</li>
     * </ul>
     *
     * <p>Both dimensions are evaluated independently — a slow success and a fast
     * failure are different kinds of SLA violations.
     */
    private static final ServiceContract<PaymentInput, PaymentResult> CONTRACT =
            ServiceContract.<PaymentInput, PaymentResult>define()
                    .ensure("Transaction succeeded", pr -> pr.success() ? Outcome.ok() : Outcome.fail("check","transaction failed: " + pr.errorCode()))
                    .ensureDurationBelow("SLA", Duration.ofSeconds(1))
                    .build();

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
     * <p>This method uses the fluent {@link UseCaseOutcome} builder API which:
     * <ul>
     *   <li>Automatically captures execution timing</li>
     *   <li>Evaluates postconditions lazily</li>
     *   <li>Bundles metadata with the result</li>
     * </ul>
     *
     * @param cardToken the tokenized card reference
     * @param amountCents the amount to charge in cents
     * @return outcome containing typed result and postconditions
     */
    public UseCaseOutcome<PaymentResult> chargeCard(String cardToken, long amountCents) {
        return UseCaseOutcome
                .withContract(CONTRACT)
                .input(new PaymentInput(cardToken, amountCents))
                .execute(this::executeCharge)
                .meta("region", region)
                .build();
    }

    /**
     * Executes the payment charge through the gateway.
     *
     * <p>This method is called by the fluent builder's {@code execute()} step.
     *
     * @param input the payment input parameters
     * @return the payment result from the gateway
     */
    private PaymentResult executeCharge(PaymentInput input) {
        return gateway.charge(input.cardToken(), input.amountCents());
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
    @FactorProvider
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
    @FactorProvider
    public static List<FactorArguments> singlePayment() {
        return FactorArguments.configurations()
                .names("cardToken", "amountCents")
                .values("tok_visa_4242", 1999L)
                .stream().toList();
    }
}
