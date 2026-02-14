package org.javai.punit.examples.usecases;

import java.time.Duration;
import org.javai.outcome.Outcome;
import org.javai.punit.api.FactorGetter;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.RegionGroup;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.examples.app.payment.MockPaymentGateway;
import org.javai.punit.examples.app.payment.PaymentGateway;
import org.javai.punit.examples.app.payment.PaymentResult;
import org.jspecify.annotations.NonNull;

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
 * @see org.javai.punit.examples.probabilistictests.PaymentGatewaySlaTest
 */
@UseCase(description = "Process payment transactions through a payment gateway",
		covariateRegion = @RegionGroup("us-east-1"))
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
					.ensure("Transaction succeeded", PaymentGatewayUseCase::isSuccess)
					.ensureDurationBelow("SLA", Duration.ofSeconds(1))
					.build();

	private static @NonNull Outcome<Void> isSuccess(PaymentResult pr) {
		return pr.success() ? Outcome.ok() : Outcome.fail("check", "transaction failed: " + pr.errorCode());
	}

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

	@FactorGetter
	public String getRegion() {
		return region;
	}

	@FactorSetter("region")
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

}
