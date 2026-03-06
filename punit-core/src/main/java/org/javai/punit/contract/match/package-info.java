/**
 * Instance conformance matching for use case outcomes.
 *
 * <p>This package provides infrastructure for comparing actual execution results
 * against expected values, enabling instance conformance checking in PUnit experiments.
 *
 * <h2>Core Interfaces</h2>
 * <ul>
 *   <li>{@link org.javai.punit.contract.match.VerificationMatcher} - Compares expected and actual values</li>
 *   <li>{@link org.javai.punit.contract.match.ResultExtractor} - Extracts matchable values from results</li>
 * </ul>
 *
 * <h2>Built-in Matchers</h2>
 * <ul>
 *   <li>{@link org.javai.punit.contract.match.StringMatcher} - String comparison with modes (exact, ignore case, etc.)</li>
 *   <li>{@link org.javai.punit.contract.match.JsonMatcher} - JSON semantic comparison (optional dependency)</li>
 * </ul>
 *
 * <h2>Usage with UseCaseOutcome</h2>
 * <pre>{@code
 * UseCaseOutcome<String> outcome = UseCaseOutcome
 *     .withContract(CONTRACT)
 *     .input(input)
 *     .execute(service::call)
 *     .expecting("expected result", ResultExtractor.identity(), StringMatcher.exact())
 *     .build();
 *
 * // Check conformance
 * if (outcome.fullySatisfied()) {
 *     // Both postconditions passed AND expected value matched
 * }
 * }</pre>
 *
 * @see org.javai.punit.contract.UseCaseOutcome
 */
package org.javai.punit.contract.match;
