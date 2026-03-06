package org.javai.punit.contract;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.javai.outcome.Outcome;

/**
 * A service contract defines the postconditions for a service.
 *
 * <p>A contract consists of:
 * <ul>
 *   <li>Postconditions — conditions on the raw result (no transformation needed)</li>
 *   <li>Derivations — transformations of the result with associated postconditions</li>
 * </ul>
 *
 * <p>The contract specifies what the external service promises to deliver. It does not
 * include preconditions because the UseCase must be a faithful, transparent wrapper
 * that exposes the service's actual behavior—including any missing input validation.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract
 *     .<ServiceInput, String>define()
 *
 *     .ensure("Response not empty", response ->
 *         response.isEmpty() ? Outcome.fail("check", "was empty") : Outcome.ok())
 *
 *     .derive("Valid JSON", MyUseCase::parseJson)
 *         .ensure("Has operations array", json ->
 *             json.has("operations") ? Outcome.ok() : Outcome.fail("check", "missing operations"))
 *         .ensure("All operations valid", MyUseCase::validateOperations)
 *
 *     .build();
 * }</pre>
 *
 * @param <I> the input type
 * @param <R> the result type (postconditions and derivations evaluate against this)
 * @see Derivation
 * @see Postcondition
 */
public final class ServiceContract<I, R> implements PostconditionEvaluator<R> {

    private final List<Postcondition<R>> postconditions;
    private final List<Derivation<R, ?>> derivations;
    private final DurationConstraint durationConstraint;

    private ServiceContract(
            List<Postcondition<R>> postconditions,
            List<Derivation<R, ?>> derivations,
            DurationConstraint durationConstraint) {
        this.postconditions = List.copyOf(postconditions);
        this.derivations = List.copyOf(derivations);
        this.durationConstraint = durationConstraint;
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
     * Returns the duration constraint, if any.
     *
     * <p>Duration constraints are evaluated independently from postconditions,
     * providing a parallel dimension of success/failure for timing requirements.
     *
     * @return the duration constraint, or empty if none specified
     */
    public Optional<DurationConstraint> durationConstraint() {
        return Optional.ofNullable(durationConstraint);
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
     *     .ensure(...)
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
        return "ServiceContract[derivations=" + derivations.size() +
                ", postconditions=" + postconditionCount() + "]";
    }

    /**
     * Builder for creating service contracts.
     *
     * @param <I> the input type
     * @param <R> the result type
     */
    public static final class ContractBuilder<I, R> {

        private final List<Postcondition<R>> postconditions = new ArrayList<>();
        private final List<Derivation<R, ?>> derivations = new ArrayList<>();
        private DurationConstraint durationConstraint;

        private ContractBuilder() {
        }

        /**
         * Adds a postcondition on the raw result.
         *
         * <p>This postcondition is evaluated directly against the result without
         * any derivation transformation. Return {@code Outcome.ok()} for success
         * or {@code Outcome.fail("check", reason)} to indicate failure with details.
         *
         * <p>Example:
         * <pre>{@code
         * .ensure("Response not empty", response ->
         *     response.isEmpty() ? Outcome.fail("check", "was empty") : Outcome.ok())
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
        public <D> DerivingBuilder<I, R, D> derive(String description, Function<R, Outcome<D>> function) {
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(function, "function must not be null");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
            return new DerivingBuilder<>(this, description, function);
        }

        /**
         * Adds a duration constraint to the contract.
         *
         * <p>The constraint is evaluated independently from postconditions.
         * Both dimensions (correctness and timing) are always reported,
         * regardless of whether one or both fail.
         *
         * <p>Example:
         * <pre>{@code
         * ServiceContract.<Input, Response>define()
         *     .ensure("Response has content", ...)
         *     .ensureDurationBelow(Duration.ofMillis(500))
         *     .build();
         * }</pre>
         *
         * @param maxDuration the maximum allowed execution duration
         * @return this builder
         * @throws NullPointerException if maxDuration is null
         * @throws IllegalArgumentException if maxDuration is not positive
         */
        public ContractBuilder<I, R> ensureDurationBelow(Duration maxDuration) {
            this.durationConstraint = DurationConstraint.of(maxDuration);
            return this;
        }

        /**
         * Adds a duration constraint with a custom description.
         *
         * <p>The constraint is evaluated independently from postconditions.
         * Both dimensions (correctness and timing) are always reported.
         *
         * @param description the constraint description
         * @param maxDuration the maximum allowed execution duration
         * @return this builder
         * @throws NullPointerException if description or maxDuration is null
         * @throws IllegalArgumentException if maxDuration is not positive
         */
        public ContractBuilder<I, R> ensureDurationBelow(String description, Duration maxDuration) {
            this.durationConstraint = DurationConstraint.of(description, maxDuration);
            return this;
        }

        /**
         * Builds the service contract.
         *
         * @return the immutable service contract
         */
        public ServiceContract<I, R> build() {
            return new ServiceContract<>(postconditions, derivations, durationConstraint);
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
         * <p>Return {@code Outcome.ok()} for success or {@code Outcome.fail("check", reason)}
         * to indicate failure with details.
         *
         * <p>Example:
         * <pre>{@code
         * .deriving("Parse JSON", this::parseJson)
         *     .ensure("Has operations", json ->
         *         json.has("operations") ? Outcome.ok() : Outcome.fail("check", "missing operations"))
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
        public <D2> DerivingBuilder<I, R, D2> derive(String description, Function<R, Outcome<D2>> function) {
            finalizeCurrent();
            return parent.derive(description, function);
        }

        /**
         * Adds a duration constraint to the contract.
         *
         * <p>Finalizes the current derivation and adds the duration constraint.
         *
         * @param maxDuration the maximum allowed execution duration
         * @return the parent contract builder
         */
        public ContractBuilder<I, R> ensureDurationBelow(Duration maxDuration) {
            finalizeCurrent();
            return parent.ensureDurationBelow(maxDuration);
        }

        /**
         * Adds a duration constraint with a custom description.
         *
         * <p>Finalizes the current derivation and adds the duration constraint.
         *
         * @param description the constraint description
         * @param maxDuration the maximum allowed execution duration
         * @return the parent contract builder
         */
        public ContractBuilder<I, R> ensureDurationBelow(String description, Duration maxDuration) {
            finalizeCurrent();
            return parent.ensureDurationBelow(description, maxDuration);
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
