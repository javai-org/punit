package org.javai.punit.contract;

import java.util.Objects;

/**
 * Thrown when a precondition is violated.
 *
 * <p>Precondition violations represent programming errors — the caller provided
 * invalid input. This is not an outcome to be handled gracefully; it demands a fix.
 *
 * <h2>When This Is Thrown</h2>
 * <p>This exception is thrown eagerly when {@code input()} is called on the
 * outcome builder and a precondition from the service contract fails:
 * <pre>{@code
 * return UseCaseOutcome
 *     .withContract(CONTRACT)
 *     .input(new ServiceInput(null, instruction, temperature))  // throws!
 *     // ...
 * }</pre>
 *
 * <h2>Design by Contract</h2>
 * <p>In Design by Contract terms, preconditions define what the service requires
 * from its caller. A violated precondition means the caller broke the contract,
 * not the service.
 *
 * @see ServiceContract
 */
public class PreconditionException extends RuntimeException {

    private final String preconditionDescription;
    private final Object input;

    /**
     * Creates a new precondition exception.
     *
     * @param preconditionDescription the description of the violated precondition
     * @param input the input that violated the precondition (may be null)
     */
    public PreconditionException(String preconditionDescription, Object input) {
        super("Precondition violated: " + preconditionDescription);
        this.preconditionDescription = Objects.requireNonNull(
                preconditionDescription, "preconditionDescription must not be null");
        this.input = input;
    }

    /**
     * Returns the description of the violated precondition.
     *
     * @return the precondition description
     */
    public String getPreconditionDescription() {
        return preconditionDescription;
    }

    /**
     * Returns the input that violated the precondition.
     *
     * @return the input value (may be null)
     */
    public Object getInput() {
        return input;
    }
}
