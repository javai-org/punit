/**
 * Service contract types for Design by Contract support in PUnit.
 *
 * <p>This package provides types for expressing service contracts — the preconditions
 * a service requires and the postconditions it guarantees. These contracts are
 * formalized in use case code since Java lacks Eiffel's built-in Design by Contract
 * support.
 *
 * <h2>Core Types</h2>
 * <ul>
 *   <li>{@link org.javai.punit.contract.ServiceContract} — The contract definition with preconditions and postconditions</li>
 *   <li>{@link org.javai.punit.contract.UseCaseOutcome} — The outcome of a use case execution with result, timing, and postconditions</li>
 *   <li>{@link org.javai.punit.contract.PostconditionEvaluator} — Interface for evaluating postconditions against a result</li>
 *   <li>{@link org.javai.punit.contract.Outcomes} — Factory methods for creating Outcome instances in derivations</li>
 *   <li>{@link org.javai.punit.contract.Precondition} — A single require clause with description and predicate</li>
 *   <li>{@link org.javai.punit.contract.Postcondition} — A single ensure clause with description and predicate</li>
 *   <li>{@link org.javai.punit.contract.Derivation} — Transforms raw result into derived perspective for postconditions</li>
 *   <li>{@link org.javai.punit.contract.PostconditionResult} — Evaluation result (Passed, Failed, or Skipped)</li>
 *   <li>{@link org.javai.punit.contract.PreconditionException} — Thrown when a precondition is violated</li>
 * </ul>
 *
 * <h2>Design by Contract Vocabulary</h2>
 * <p>Borrowing from Eiffel:
 * <table border="1">
 *   <tr><th>Clause</th><th>Purpose</th><th>Evaluation</th><th>On Failure</th></tr>
 *   <tr><td>{@code require}</td><td>Preconditions</td><td>Eager</td><td>Throws exception</td></tr>
 *   <tr><td>{@code ensure}</td><td>Postconditions</td><td>Lazy</td><td>Recorded for analysis</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * private record ServiceInput(String prompt, String instruction) {}
 *
 * private static final ServiceContract<ServiceInput, String> CONTRACT = ServiceContract
 *     .<ServiceInput, String>define()
 *     .require("Prompt not null", in -> in.prompt() != null)
 *     .require("Instruction not blank", in -> !in.instruction().isBlank())
 *     .ensure("Response not empty", response -> !response.isEmpty())
 *     .deriving("Valid JSON", MyUseCase::parseJson)
 *         .ensure("Has operations array", json -> json.has("operations"))
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
