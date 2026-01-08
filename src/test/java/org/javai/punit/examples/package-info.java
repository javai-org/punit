/**
 * Example probabilistic tests demonstrating PUNIT features.
 *
 * <p><b>IMPORTANT:</b> All tests in this package contain failing samples BY DESIGN.
 * They use random or simulated behavior to demonstrate real-world probabilistic
 * testing scenarios where:
 * <ul>
 *   <li>Individual invocations may fail while the overall test passes</li>
 *   <li>Budget constraints may cause early termination</li>
 *   <li>Pass rate thresholds determine success, not individual sample outcomes</li>
 * </ul>
 *
 * <h2>Console Output</h2>
 * <p>When running probabilistic tests, you will see assertion failure messages
 * for individual samples that fail. This is expected behavior — PUnit re-throws
 * sample failures so they appear as ❌ in the IDE test runner for accurate
 * visual feedback. The noise from these individual failures is normal; what
 * matters is whether the overall test passes based on the pass rate threshold.
 *
 * <p>These tests are {@code @Disabled} by default to avoid running in CI.
 * Enable them individually to explore PUNIT features.
 *
 * @see org.javai.punit.api.ProbabilisticTest
 */
package org.javai.punit.examples;

