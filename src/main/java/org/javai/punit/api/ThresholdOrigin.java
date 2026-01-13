package org.javai.punit.api;

/**
 * Indicates the origin of a probabilistic test's threshold.
 *
 * <p>This enum documents where the {@code minPassRate} threshold came from,
 * enabling traceability between test configuration and business requirements,
 * and determining how hypothesis tests and verdicts should be framed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ProbabilisticTest(
 *     samples = 100,
 *     minPassRate = 0.95,
 *     thresholdOrigin = ThresholdOrigin.SLA,
 *     contractRef = "Acme API SLA v3.2 §2.1"
 * )
 * void serviceReturnsValidResponse() { ... }
 * }</pre>
 *
 * <h2>Available Origins</h2>
 * <ul>
 *   <li>{@link #UNSPECIFIED} - Default; threshold origin not documented</li>
 *   <li>{@link #SLA} - Service Level Agreement (external contract)</li>
 *   <li>{@link #SLO} - Service Level Objective (internal target)</li>
 *   <li>{@link #POLICY} - Organizational policy or compliance requirement</li>
 *   <li>{@link #EMPIRICAL} - Derived from baseline measurement</li>
 * </ul>
 *
 * <h2>Impact on Hypothesis Framing</h2>
 * <p>The threshold origin affects how PUnit frames hypothesis tests and verdicts:
 * <ul>
 *   <li>{@code SLA}: "System meets SLA requirement" / "System violates SLA"</li>
 *   <li>{@code SLO}: "System meets SLO target" / "System falls short of SLO"</li>
 *   <li>{@code POLICY}: "System meets policy requirement" / "System violates policy"</li>
 *   <li>{@code EMPIRICAL}: "No degradation from baseline" / "Degradation from baseline"</li>
 *   <li>{@code UNSPECIFIED}: "Success rate meets threshold" / "Success rate below threshold"</li>
 * </ul>
 *
 * @see ProbabilisticTest#thresholdOrigin()
 * @see ProbabilisticTest#contractRef()
 */
public enum ThresholdOrigin {

    /**
     * No origin specified (default).
     * The threshold is ad-hoc or its origin is not documented.
     */
    UNSPECIFIED,

    /**
     * Threshold derived from a Service Level Agreement (SLA).
     * SLAs are typically contractual commitments to external customers.
     */
    SLA,

    /**
     * Threshold derived from a Service Level Objective (SLO).
     * SLOs are internal targets that may be more stringent than SLAs.
     */
    SLO,

    /**
     * Threshold derived from an organizational policy.
     * Policies may include security requirements, compliance mandates, etc.
     */
    POLICY,

    /**
     * Threshold derived from empirical measurement.
     * This indicates the threshold was established through observation
     * (e.g., baseline experiments) rather than contractual requirements.
     */
    EMPIRICAL
}

