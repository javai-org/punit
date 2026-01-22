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
 *   <li>{@link org.javai.punit.contract.Outcome} — Result type for fallible operations (success or failure)</li>
 *   <li>{@link org.javai.punit.contract.Postcondition} — A single ensure clause with description and predicate</li>
 *   <li>{@link org.javai.punit.contract.PostconditionResult} — Evaluation result (Passed, Failed, or Skipped)</li>
 *   <li>{@link org.javai.punit.contract.UseCasePreconditionException} — Thrown when a precondition is violated</li>
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
 * @see org.javai.punit.contract.Outcome
 * @see org.javai.punit.contract.Postcondition
 * @see org.javai.punit.contract.PostconditionResult
 */
package org.javai.punit.contract;
