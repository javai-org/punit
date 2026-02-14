/**
 * Example probabilistic tests demonstrating PUnit's testing capabilities.
 *
 * <p>This package contains <b>working test examples</b> that illustrate how to write
 * probabilistic tests for non-deterministic systems. Each test class focuses on a
 * specific feature or pattern.
 *
 * <h2>Threshold Determination</h2>
 * <p>{@link org.javai.punit.examples.probabilistictests.ShoppingBasketThresholdApproachesTest}
 * demonstrates the three operational approaches:
 * <ul>
 *   <li><b>Sample-Size-First</b> - Fixed budget, statistically-derived threshold</li>
 *   <li><b>Confidence-First</b> - Statistical power requirements drive sample size</li>
 *   <li><b>Threshold-First</b> - Known SLA or policy threshold</li>
 * </ul>
 *
 * <h2>Covariate-Aware Testing</h2>
 * <p>{@link org.javai.punit.examples.probabilistictests.ShoppingBasketCovariateTest} shows how
 * baselines can vary by temporal, infrastructure, or configuration factors, enabling
 * tests to automatically select the most appropriate baseline.
 *
 * <h2>Resource Management</h2>
 * <ul>
 *   <li>{@link org.javai.punit.examples.probabilistictests.ShoppingBasketBudgetTest} - Token and
 *       time budgets for cost control</li>
 *   <li>{@link org.javai.punit.examples.probabilistictests.ShoppingBasketPacingTest} - Rate limiting
 *       for API compliance</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>{@link org.javai.punit.examples.probabilistictests.ShoppingBasketExceptionTest} demonstrates
 * strategies for handling exceptions: fail sample, propagate immediately, or ignore.
 *
 * <h2>SLA Verification</h2>
 * <p>{@link org.javai.punit.examples.probabilistictests.PaymentGatewaySlaTest} shows the
 * <b>contractual approach</b>: when you have an external SLA (e.g., "99.5% success"),
 * you can specify the threshold directly rather than deriving it from experiments.
 *
 * <h2>Running Tests</h2>
 * <p>Tests are {@code @Disabled} by default. Run individually after setting up baselines:
 * <pre>{@code
 * # First, run the corresponding MEASURE experiment to establish a baseline
 * ./gradlew exp -Prun=ShoppingBasketMeasure
 *
 * # Then run the test
 * ./gradlew test --tests "ShoppingBasketTest"
 * }</pre>
 *
 * @see org.javai.punit.api.ProbabilisticTest
 * @see org.javai.punit.examples.experiments
 */
package org.javai.punit.examples.probabilistictests;
