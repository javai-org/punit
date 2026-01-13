/**
 * Baseline expiration support for PUnit.
 *
 * <p>This package provides infrastructure for evaluating and reporting
 * baseline expiration status:
 *
 * <ul>
 *   <li>{@link org.javai.punit.engine.expiration.ExpirationEvaluator} - 
 *       Evaluates expiration status for specifications</li>
 *   <li>{@link org.javai.punit.engine.expiration.ExpirationWarningRenderer} - 
 *       Renders human-readable warnings</li>
 *   <li>{@link org.javai.punit.engine.expiration.ExpirationReportPublisher} - 
 *       Publishes machine-readable properties</li>
 *   <li>{@link org.javai.punit.engine.expiration.WarningLevel} - 
 *       Maps status to verbosity levels</li>
 * </ul>
 *
 * @see org.javai.punit.model.ExpirationPolicy
 * @see org.javai.punit.model.ExpirationStatus
 */
package org.javai.punit.engine.expiration;

