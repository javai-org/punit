/**
 * Probabilistic testing framework for validating non-deterministic systems against specifications.
 *
 * <p>This package provides infrastructure for running probabilistic tests that execute
 * multiple samples and determine pass/fail based on statistical thresholds rather than
 * binary success/failure of individual samples.
 *
 * <p>Key concepts:
 * <ul>
 *   <li><b>Samples:</b> Individual test executions that may pass or fail</li>
 *   <li><b>Pass Rate:</b> The ratio of successful samples to total samples</li>
 *   <li><b>Threshold:</b> The minimum required pass rate for the test to pass</li>
 *   <li><b>Early Termination:</b> Stopping execution when success becomes guaranteed or impossible</li>
 * </ul>
 *
 * <p>The {@link org.javai.punit.ptest.engine.ProbabilisticTestExtension} coordinates test
 * execution using the Strategy pattern. Currently supports:
 * <ul>
 *   <li><b>Bernoulli Trials</b> ({@link org.javai.punit.ptest.bernoulli.BernoulliTrialsStrategy}) -
 *       Models each sample as a Bernoulli trial (success/failure) and uses one-sided
 *       inference to determine pass/fail</li>
 * </ul>
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@code engine/} - Core extension and shared infrastructure</li>
 *   <li>{@code strategy/} - Strategy interface and common types</li>
 *   <li>{@code bernoulli/} - Bernoulli trials strategy implementation</li>
 * </ul>
 *
 * @see org.javai.punit.api.ProbabilisticTest
 * @see org.javai.punit.experiment
 * @see org.javai.punit.spec
 */
package org.javai.punit.ptest;
