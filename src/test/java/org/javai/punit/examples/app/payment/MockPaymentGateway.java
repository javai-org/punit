package org.javai.punit.examples.app.payment;

import java.util.Random;
import java.util.UUID;

/**
 * Mock implementation of {@link PaymentGateway} that simulates SLA-bound reliability.
 *
 * <p>This singleton mock is designed for demonstrating SLA-driven probabilistic testing.
 * It simulates a payment gateway with the following characteristics:
 *
 * <h2>SLA Target vs Actual Performance</h2>
 * <ul>
 *   <li><b>SLA Target:</b> 99.99% availability (contractual)</li>
 *   <li><b>Actual Performance:</b> ~99.97% (slightly below SLA)</li>
 * </ul>
 *
 * <p>This intentional gap allows tests to detect when actual performance doesn't
 * meet the contractual SLA, which is the purpose of SLA-driven testing.
 *
 * <h2>Error Codes</h2>
 * <p>When failures occur, they use realistic error codes:
 * <ul>
 *   <li>{@code DECLINED} - Card declined by issuer</li>
 *   <li>{@code TIMEOUT} - Network timeout</li>
 *   <li>{@code NETWORK_ERROR} - Connection failure</li>
 *   <li>{@code INSUFFICIENT_FUNDS} - Account balance too low</li>
 *   <li>{@code CARD_EXPIRED} - Card expiration date passed</li>
 *   <li>{@code FRAUD_SUSPECTED} - Transaction flagged as suspicious</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PaymentGateway gateway = MockPaymentGateway.instance();
 * PaymentResult result = gateway.charge("tok_visa_4242", 1999);
 * }</pre>
 */
public final class MockPaymentGateway implements PaymentGateway {

    private static final MockPaymentGateway INSTANCE = new MockPaymentGateway();

    /**
     * Actual failure rate: ~0.03% (99.97% success).
     * This is intentionally below the 99.99% SLA to demonstrate SLA testing.
     */
    private static final double FAILURE_RATE = 0.0003;

    private static final String[] ERROR_CODES = {
            "DECLINED",
            "TIMEOUT",
            "NETWORK_ERROR",
            "INSUFFICIENT_FUNDS",
            "CARD_EXPIRED",
            "FRAUD_SUSPECTED"
    };

    private final Random random;
    private long seed;

    private MockPaymentGateway() {
        this.seed = System.currentTimeMillis();
        this.random = new Random(seed);
    }

    /**
     * Returns the singleton instance.
     *
     * @return the shared MockPaymentGateway instance
     */
    public static MockPaymentGateway instance() {
        return INSTANCE;
    }

    /**
     * Resets the random seed for reproducible test runs.
     *
     * @param seed the seed value
     */
    public void setSeed(long seed) {
        this.seed = seed;
        this.random.setSeed(seed);
    }

    /**
     * Returns the current seed.
     *
     * @return the seed value
     */
    public long getSeed() {
        return seed;
    }

    @Override
    public PaymentResult charge(String cardToken, long amountCents) {
        // Simulate network latency
        simulateLatency();

        // Check for failure
        if (random.nextDouble() < FAILURE_RATE) {
            String errorCode = ERROR_CODES[random.nextInt(ERROR_CODES.length)];
            return PaymentResult.failure(errorCode);
        }

        // Generate transaction ID
        String transactionId = "txn_" + UUID.randomUUID().toString().substring(0, 12);
        return PaymentResult.success(transactionId);
    }

    private void simulateLatency() {
        // Simulate 50-200ms latency
        try {
            Thread.sleep(50 + random.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
