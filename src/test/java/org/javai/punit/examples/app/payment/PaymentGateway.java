package org.javai.punit.examples.app.payment;

/**
 * Interface for payment processing operations.
 *
 * <p>This abstraction represents a payment gateway that processes card transactions.
 * It's designed for demonstrating SLA-based probabilistic testing where thresholds
 * come from contractual agreements rather than empirical baselines.
 *
 * <p>In the examples, this is implemented by {@link MockPaymentGateway} which
 * simulates realistic payment processing behavior with configurable reliability.
 */
public interface PaymentGateway {

    /**
     * Charges a card for the specified amount.
     *
     * @param cardToken the tokenized card reference
     * @param amountCents the amount to charge in cents
     * @return the result of the payment attempt
     */
    PaymentResult charge(String cardToken, long amountCents);
}
