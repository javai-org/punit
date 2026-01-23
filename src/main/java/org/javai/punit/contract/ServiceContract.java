package org.javai.punit.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.javai.outcome.Outcome;

/**
 * A service contract defines the preconditions and postconditions for a service.
 *
 * <p>A contract consists of:
 * <ul>
 *   <li>Preconditions — what the service requires from its caller (checked eagerly)</li>
 *   <li>Postconditions — conditions on the raw result (no transformation needed)</li>
 *   <li>Derivations — transformations of the result with associated postconditions</li>
 * </ul>
 *
 * <h2>Design by Contract</h2>
 * <p>The contract belongs to the <b>service</b> being invoked, but since Java lacks
 * Eiffel's built-in contract support, the use case formalizes it. The contract is
 * typically declared as a {@code static final} field—a pure specification with no free
 * variables.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * private record ServiceInput(String prompt, String instruction, double temperature) {}
 *
 * private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract
 *     .<ServiceInput, String>define()
 *
 *     .require("Prompt not null", in -> in.prompt() != null)
 *     .require("Instruction not blank", in -> !in.instruction().isBlank())
 *
 *     .ensure("Response not empty", response ->
 *         response.isEmpty() ? Outcomes.fail("was empty") : Outcomes.okVoid())
 *
 *     .deriving("Valid JSON", MyUseCase::parseJson)
 *         .ensure("Has operations array", json ->
 *             json.has("operations") ? Outcomes.okVoid() : Outcomes.fail("missing operations"))
 *         .ensure("All operations valid", MyUseCase::validateOperations)
 *
 *     .build();
 * }</pre>
 *
 * @param <I> the input type (preconditions evaluate against this)
 * @param <R> the result type (derivations transform this)
 * @see Precondition
 * @see Derivation
 * @see Postcondition
 */
public final class ServiceContract<I, R> implements PostconditionEvaluator<R> {

    private final List<Precondition<I>> preconditions;
    private final List<Postcondition<R>> postconditions;
    private final List<Derivation<R, ?>> derivations;

    private ServiceContract(
            List<Precondition<I>> preconditions,
            List<Postcondition<R>> postconditions,
            List<Derivation<R, ?>> derivations) {
        this.preconditions = List.copyOf(preconditions);
        this.postconditions = List.copyOf(postconditions);
        this.derivations = List.copyOf(derivations);
    }

    /**
     * Returns the preconditions for this contract.
     *
     * @return unmodifiable list of preconditions
     */
    public List<Precondition<I>> preconditions() {
        return preconditions;
    }

    /**
     * Returns the direct postconditions for this contract.
     *
     * <p>These are postconditions evaluated directly against the raw result,
     * without any derivation transformation.
     *
     * @return unmodifiable list of direct postconditions
     */
    public List<Postcondition<R>> postconditions() {
        return postconditions;
    }

    /**
     * Returns the derivations for this contract.
     *
     * @return unmodifiable list of derivations
     */
    public List<Derivation<R, ?>> derivations() {
        return derivations;
    }

    /**
     * Checks all preconditions against an input value.
     *
     * @param input the input to check
     * @throws PreconditionException if any precondition fails
     */
    public void checkPreconditions(I input) {
        for (Precondition<I> precondition : preconditions) {
            precondition.check(input);
        }
    }

    /**
     * Evaluates all postconditions against a result.
     *
     * <p>Direct postconditions are evaluated first, followed by derivation postconditions.
     *
     * @param result the result to evaluate
     * @return list of all postcondition results
     */
    @Override
    public List<PostconditionResult> evaluate(R result) {
        List<PostconditionResult> results = new ArrayList<>();
        for (Postcondition<R> postcondition : postconditions) {
            results.add(postcondition.evaluate(result));
        }
        for (Derivation<R, ?> derivation : derivations) {
            results.addAll(derivation.evaluate(result));
        }
        return results;
    }

    /**
     * Returns the total number of postconditions in this contract.
     *
     * <p>This counts:
     * <ul>
     *   <li>Direct postconditions on the raw result</li>
     *   <li>Derivations (each is a postcondition in its own right)</li>
     *   <li>Ensure clauses within each derivation</li>
     * </ul>
     *
     * @return total postcondition count
     */
    @Override
    public int postconditionCount() {
        int count = postconditions.size();
        for (Derivation<R, ?> derivation : derivations) {
            count++; // The derivation itself is a postcondition
            count += derivation.postconditions().size();
        }
        return count;
    }

    /**
     * Starts building a new service contract.
     *
     * <p>Use explicit type parameters for clarity:
     * <pre>{@code
     * ServiceContract.<MyInput, MyResult>define()
     *     .require(...)
     *     .deriving(...)
     *     .build();
     * }</pre>
     *
     * @param <I> the input type
     * @param <R> the result type
     * @return a new contract builder
     */
    public static <I, R> ContractBuilder<I, R> define() {
        return new ContractBuilder<>();
    }

    @Override
    public String toString() {
        return "ServiceContract[preconditions=" + preconditions.size() +
                ", derivations=" + derivations.size() +
                ", postconditions=" + postconditionCount() + "]";
    }

    /**
     * Builder for creating service contracts.
     *
     * @param <I> the input type
     * @param <R> the result type
     */
    public static final class ContractBuilder<I, R> {

        private final List<Precondition<I>> preconditions = new ArrayList<>();
        private final List<Postcondition<R>> postconditions = new ArrayList<>();
        private final List<Derivation<R, ?>> derivations = new ArrayList<>();

        private ContractBuilder() {
        }

        /**
         * Adds a precondition to the contract.
         *
         * <p>Preconditions are checked eagerly when input is provided.
         *
         * @param description the human-readable description
         * @param predicate the condition to evaluate
         * @return this builder
         */
        public ContractBuilder<I, R> require(String description, Predicate<I> predicate) {
            preconditions.add(new Precondition<>(description, predicate));
            return this;
        }

        /**
         * Adds a postcondition on the raw result.
         *
         * <p>This postcondition is evaluated directly against the result without
         * any derivation transformation. Return {@code Outcomes.okVoid()} for success
         * or {@code Outcomes.fail(reason)} to indicate failure with details.
         *
         * <p>Example:
         * <pre>{@code
         * .ensure("Response not empty", response ->
         *     response.isEmpty() ? Outcomes.fail("was empty") : Outcomes.okVoid())
         * }</pre>
         *
         * @param description the human-readable description
         * @param check the function returning success or failure with reason
         * @return this builder
         */
        public ContractBuilder<I, R> ensure(String description, PostconditionCheck<R> check) {
            postconditions.add(new Postcondition<>(description, check));
            return this;
        }

        /**
         * Starts a derivation with a description.
         *
         * <p>The description names this derivation as an ensure clause in its own right.
         * If the derivation fails, nested ensures are skipped.
         *
         * @param description the description (this becomes a postcondition)
         * @param function the derivation function
         * @param <D> the derived type
         * @return a deriving builder for adding postconditions
         */
        public <D> DerivingBuilder<I, R, D> deriving(String description, Function<R, Outcome<D>> function) {
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(function, "function must not be null");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
            return new DerivingBuilder<>(this, description, function);
        }

        /**
         * Builds the service contract.
         *
         * @return the immutable service contract
         */
        public ServiceContract<I, R> build() {
            return new ServiceContract<>(preconditions, postconditions, derivations);
        }

        void addDerivation(Derivation<R, ?> derivation) {
            derivations.add(derivation);
        }
    }

    /**
     * Builder for adding postconditions to a derivation.
     *
     * @param <I> the input type
     * @param <R> the result type
     * @param <D> the derived type
     */
    public static final class DerivingBuilder<I, R, D> {

        private final ContractBuilder<I, R> parent;
        private final String description;
        private final Function<R, Outcome<D>> function;
        private final List<Postcondition<D>> postconditions = new ArrayList<>();

        private DerivingBuilder(ContractBuilder<I, R> parent, String description, Function<R, Outcome<D>> function) {
            this.parent = parent;
            this.description = description;
            this.function = function;
        }

        /**
         * Adds a postcondition to this derivation.
         *
         * <p>Return {@code Outcomes.okVoid()} for success or {@code Outcomes.fail(reason)}
         * to indicate failure with details.
         *
         * <p>Example:
         * <pre>{@code
         * .deriving("Parse JSON", this::parseJson)
         *     .ensure("Has operations", json ->
         *         json.has("operations") ? Outcomes.okVoid() : Outcomes.fail("missing operations"))
         * }</pre>
         *
         * @param description the human-readable description
         * @param check the function returning success or failure with reason
         * @return this builder
         */
        public DerivingBuilder<I, R, D> ensure(String description, PostconditionCheck<D> check) {
            postconditions.add(new Postcondition<>(description, check));
            return this;
        }

        /**
         * Starts a new derivation.
         *
         * <p>Finalizes the current derivation and starts a new one.
         *
         * @param description the description for the new derivation
         * @param function the new derivation function
         * @param <D2> the new derived type
         * @return a deriving builder for the new derivation
         */
        public <D2> DerivingBuilder<I, R, D2> deriving(String description, Function<R, Outcome<D2>> function) {
            finalizeCurrent();
            return parent.deriving(description, function);
        }

        /**
         * Builds the service contract.
         *
         * <p>Finalizes the current derivation and returns the contract.
         *
         * @return the immutable service contract
         */
        public ServiceContract<I, R> build() {
            finalizeCurrent();
            return parent.build();
        }

        private void finalizeCurrent() {
            parent.addDerivation(new Derivation<>(description, function, postconditions));
        }
    }
}
