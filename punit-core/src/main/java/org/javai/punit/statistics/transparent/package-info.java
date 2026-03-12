/**
 * Transparent Statistics Mode configuration and vocabulary.
 *
 * <p>This package provides configuration and symbol constants for transparent
 * statistics output. Rendering is handled by
 * {@link org.javai.punit.verdict.VerdictTextRenderer}.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.javai.punit.statistics.transparent.TransparentStatsConfig} -
 *       Configuration with precedence: annotation &gt; system property &gt; env var &gt; default</li>
 *   <li>{@link org.javai.punit.statistics.transparent.StatisticalVocabulary} -
 *       Mathematical symbols with Unicode/ASCII fallback</li>
 *   <li>{@link org.javai.punit.statistics.transparent.BaselineData} -
 *       Baseline data transfer object</li>
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
