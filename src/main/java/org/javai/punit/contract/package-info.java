/**
 * Service contract types for postcondition verification in PUnit.
 *
 * <p>This package provides types for expressing service contracts — the postconditions
 * a service guarantees. The UseCase acts as a transparent wrapper that exposes the
 * service's actual behavior, including any missing validation the service should have.
 *
 * <h2>Core Types</h2>
 * <ul>
 *   <li>{@link org.javai.punit.contract.ServiceContract} — The contract definition with postconditions</li>
 *   <li>{@link org.javai.punit.contract.UseCaseOutcome} — The outcome of a use case execution with result, timing, and postconditions</li>
 *   <li>{@link org.javai.punit.contract.PostconditionEvaluator} — Interface for evaluating postconditions against a result</li>
 *   <li>{@link org.javai.punit.contract.Outcomes} — Factory methods for creating Outcome instances in derivations</li>
 *   <li>{@link org.javai.punit.contract.Postcondition} — A single ensure clause with description and check function</li>
 *   <li>{@link org.javai.punit.contract.Derivation} — Transforms raw result into derived perspective for postconditions</li>
 *   <li>{@link org.javai.punit.contract.PostconditionResult} — Evaluation result (Passed, Failed, or Skipped)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * private record ServiceInput(String prompt, String instruction) {}
 *
 * private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract
 *     .<ServiceInput, String>define()
 *     .ensure("Response not empty", response ->
 *         response.isEmpty() ? Outcomes.fail("was empty") : Outcomes.okVoid())
 *     .deriving("Valid JSON", MyUseCase::parseJson)
 *         .ensure("Has operations array", json ->
 *             json.has("operations") ? Outcomes.okVoid() : Outcomes.fail("missing"))
 *     .build();
 *
 * public UseCaseOutcome<String> translateInstruction(String instruction) {
 *     return UseCaseOutcome
 *         .withContract(CONTRACT)
 *         .input(new ServiceInput(systemPrompt, instruction))
 *         .execute(in -> llm.chat(in.prompt(), in.instruction()))
 *         .meta("tokensUsed", llm.getLastTokensUsed())
 *         .build();
 * }
 * }</pre>
 *
 * @see org.javai.punit.contract.ServiceContract
 * @see org.javai.punit.contract.UseCaseOutcome
 * @see org.javai.punit.contract.Outcomes
 * @see org.javai.punit.contract.Postcondition
 * @see org.javai.punit.contract.PostconditionResult
 */
package org.javai.punit.contract;
