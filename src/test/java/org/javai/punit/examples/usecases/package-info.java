/**
 * Domain use cases encapsulating non-deterministic behavior for testing.
 *
 * <p>A <b>use case</b> in PUnit represents a single unit of non-deterministic behavior
 * that can be tested. It encapsulates the system under test, success criteria, and
 * any factors that might affect outcomes.
 *
 * <h2>Why Use Cases?</h2>
 * <p>Use cases provide several benefits:
 * <ul>
 *   <li><b>Encapsulation</b> - All testing logic in one place</li>
 *   <li><b>Reusability</b> - Same use case for experiments and tests</li>
 *   <li><b>Factor sources</b> - Centralized test data management</li>
 *   <li><b>Covariate tracking</b> - Consistent environmental context</li>
 * </ul>
 *
 * <h2>Two Testing Approaches</h2>
 *
 * <h3>Empirical Approach</h3>
 * <p>{@link org.javai.punit.examples.usecases.ShoppingBasketUseCase} demonstrates
 * the empirical approach: you don't know the expected success rate in advance, so
 * you measure it through experiments and derive test thresholds from the baseline.
 *
 * <h3>Contractual Approach</h3>
 * <p>{@link org.javai.punit.examples.usecases.PaymentGatewayUseCase} demonstrates
 * the contractual approach: an external SLA (e.g., "99.5% success rate") defines
 * the expected behavior, so you verify compliance against that contract.
 *
 * <h2>Use Case Structure</h2>
 * <p>Use cases are plain Java classes that:
 * <ul>
 *   <li>Return outcomes bundled with success criteria</li>
 *   <li>Define postconditions that determine success/failure</li>
 *   <li>Optionally declare factor sources, covariates, and treatment setters</li>
 * </ul>
 *
 * @see org.javai.punit.api.UseCase
 * @see org.javai.punit.contract.ServiceContract
 */
package org.javai.punit.examples.usecases;
