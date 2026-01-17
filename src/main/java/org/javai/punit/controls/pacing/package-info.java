/**
 * Pacing controls for rate-limiting sample execution during tests and experiments.
 *
 * <p>This package provides infrastructure for controlling the rate at which samples
 * are executed, essential for tests that interact with rate-limited external services
 * (e.g., LLM APIs with requests-per-minute limits).
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link PacingConfiguration} - Holds resolved pacing settings (delay, window start/end)</li>
 *   <li>{@link PacingResolver} - Resolves pacing from annotations and system properties</li>
 *   <li>{@link PacingCalculator} - Computes required delays based on configuration</li>
 *   <li>{@link PacingReporter} - Reports pacing events for observability</li>
 * </ul>
 *
 * <p>Pacing can be configured via:
 * <ul>
 *   <li>The {@link org.javai.punit.api.Pacing} annotation on test methods</li>
 *   <li>System properties (e.g., {@code punit.pacing.delayMs})</li>
 *   <li>Environment variables (e.g., {@code PUNIT_PACING_DELAY_MS})</li>
 * </ul>
 *
 * @see org.javai.punit.api.Pacing
 */
package org.javai.punit.controls.pacing;
