package org.javai.punit.examples2.infrastructure.payment;

/**
 * Result of a payment gateway transaction.
 *
 * @param success whether the transaction succeeded
 * @param transactionId unique identifier for the transaction (null on failure)
 * @param errorCode error code if failed (null on success)
 */
public record PaymentResult(
        boolean success,
        String transactionId,
        String errorCode
) {

    /**
     * Creates a successful payment result.
     *
     * @param transactionId the transaction identifier
     * @return a successful result
     */
    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }

    /**
     * Creates a failed payment result.
     *
     * @param errorCode the error code indicating failure reason
     * @return a failed result
     */
    public static PaymentResult failure(String errorCode) {
        return new PaymentResult(false, null, errorCode);
    }
}
