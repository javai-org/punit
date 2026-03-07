/**
 * Transparent Statistics Mode for PUnit.
 *
 * <p>This package provides comprehensive statistical explanations for probabilistic
 * test verdicts. When enabled, it produces detailed output documenting the complete
 * statistical reasoning behind every pass/fail decision.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.javai.punit.statistics.transparent.TransparentStatsConfig} - 
 *       Configuration with precedence: annotation > system property > env var > default</li>
 *   <li>{@link org.javai.punit.statistics.transparent.StatisticalExplanation} - 
 *       Immutable record holding all explanation components</li>
 *   <li>{@link org.javai.punit.statistics.transparent.StatisticalExplanationBuilder} - 
 *       Builds explanations from test execution context</li>
 *   <li>{@link org.javai.punit.statistics.transparent.ExplanationRenderer} - 
 *       Interface for rendering explanations in various formats</li>
 *   <li>{@link org.javai.punit.statistics.transparent.TextExplanationRenderer} -
 *       Human-readable text output with box drawing</li>
 * </ul>
 *
 * <h2>Enabling Transparent Mode</h2>
 * <pre>
 * # Via system property
 * ./gradlew test -Dpunit.stats.transparent=true
 *
 * # Via environment variable
 * PUNIT_STATS_TRANSPARENT=true ./gradlew test
 *
 * # Via annotation
 * {@literal @}ProbabilisticTest(samples = 100, transparentStats = true)
 * void myTest() { ... }
 * </pre>
 *
 * @see org.javai.punit.statistics.transparent.TransparentStatsConfig
 */
package org.javai.punit.statistics.transparent;

