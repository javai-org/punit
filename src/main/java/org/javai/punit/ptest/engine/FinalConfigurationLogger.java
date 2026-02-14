package org.javai.punit.ptest.engine;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.reporting.RateFormat;

/**
 * Formats and logs the test configuration block shown at test start.
 *
 * <p>This class handles the logic for determining the appropriate "mode" label
 * (SLA-DRIVEN, SPEC-DRIVEN, EXPLICIT THRESHOLD) and formatting threshold information
 * with correct provenance attribution.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class FinalConfigurationLogger {

    private final PUnitReporter reporter;

    FinalConfigurationLogger(PUnitReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Data required to log the final test configuration.
     *
     * <p>This record provides a clean interface between the extension's internal
     * TestConfiguration and what the logger needs to display.
     */
    record ConfigurationData(
            int samples,
            double minPassRate,
            String specId,
            ThresholdOrigin thresholdOrigin,
            String contractRef,
            TestIntent intent,
            boolean thresholdDerived
    ) {
        /**
         * Backward-compatible constructor without thresholdDerived (defaults to false).
         */
        ConfigurationData(int samples, double minPassRate, String specId,
                          ThresholdOrigin thresholdOrigin, String contractRef,
                          TestIntent intent) {
            this(samples, minPassRate, specId, thresholdOrigin, contractRef, intent, false);
        }

        /**
         * Backward-compatible constructor without intent or thresholdDerived.
         */
        ConfigurationData(int samples, double minPassRate, String specId,
                          ThresholdOrigin thresholdOrigin, String contractRef) {
            this(samples, minPassRate, specId, thresholdOrigin, contractRef, TestIntent.VERIFICATION, false);
        }
        /**
         * Returns true if thresholdOrigin is a normative source (SLA, SLO, or POLICY).
         */
        boolean isNormativeThreshold() {
            return thresholdOrigin == ThresholdOrigin.SLA
                    || thresholdOrigin == ThresholdOrigin.SLO
                    || thresholdOrigin == ThresholdOrigin.POLICY;
        }

        /**
         * Returns true if a spec/use case ID is specified.
         */
        boolean hasSpecId() {
            return specId != null;
        }

        /**
         * Returns true if a contract reference is specified.
         */
        boolean hasContractRef() {
            return contractRef != null && !contractRef.isEmpty();
        }

        /**
         * Returns true if thresholdOrigin is specified and not UNSPECIFIED.
         */
        boolean hasThresholdOrigin() {
            return thresholdOrigin != null && thresholdOrigin != ThresholdOrigin.UNSPECIFIED;
        }

        /**
         * Returns true when the threshold was derived from a baseline spec
         * (as opposed to being normative or explicitly set).
         */
        boolean isSpecDriven() {
            return hasSpecId() && thresholdDerived() && !isNormativeThreshold();
        }
    }

    /**
     * Logs the final test configuration to the console.
     *
     * <p>The output format depends on the configuration mode:
     * <ul>
     *   <li><b>Normative threshold (SLA/SLO/POLICY)</b>: Shows "SLA-DRIVEN" etc. with explicit threshold</li>
     *   <li><b>Spec-driven</b>: Shows "SPEC-DRIVEN" with baseline-derived threshold</li>
     *   <li><b>Explicit threshold</b>: Shows "EXPLICIT THRESHOLD" for inline specification</li>
     * </ul>
     *
     * @param testName the name of the test method
     * @param config the configuration data to log
     */
    void log(String testName, ConfigurationData config) {
        if (config == null) {
            return;
        }
        reporter.reportInfo("TEST CONFIGURATION FOR: " + testName, format(config));
    }

    /**
     * Builds the formatted configuration string without logging.
     *
     * <p>Useful for testing the formatting logic independently of the reporter.
     *
     * @param config the configuration data to format
     * @return the formatted configuration string
     */
    String format(ConfigurationData config) {
        if (config == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(PUnitReporter.labelValueLn("Mode:", modeLabel(config)));
        appendIntent(sb, config);
        appendIdentifier(sb, config);
        sb.append(PUnitReporter.labelValueLn("Threshold:", thresholdText(config)));
        appendContractRef(sb, config);
        sb.append(PUnitReporter.labelValue("Samples:", String.valueOf(config.samples())));
        return sb.toString();
    }

    private String modeLabel(ConfigurationData config) {
        if (config.isNormativeThreshold()) {
            return config.thresholdOrigin().name() + "-DRIVEN";
        }
        if (config.isSpecDriven()) {
            return "SPEC-DRIVEN";
        }
        return "EXPLICIT THRESHOLD";
    }

    private void appendIntent(StringBuilder sb, ConfigurationData config) {
        if (config.intent() != null) {
            sb.append(PUnitReporter.labelValueLn("Intent:", config.intent().name()));
        }
    }

    private void appendIdentifier(StringBuilder sb, ConfigurationData config) {
        if (config.isSpecDriven()) {
            sb.append(PUnitReporter.labelValueLn("Spec:", config.specId()));
        } else if (config.hasSpecId()) {
            sb.append(PUnitReporter.labelValueLn("Use Case:", config.specId()));
        }
    }

    private String thresholdText(ConfigurationData config) {
        String rate = RateFormat.format(config.minPassRate());
        if (config.isNormativeThreshold()) {
            return String.format("%s (%s)", rate, config.thresholdOrigin().name());
        }
        if (config.isSpecDriven()) {
            return String.format("%s (derived from baseline)", rate);
        }
        if (config.hasThresholdOrigin()) {
            return String.format("%s (%s)", rate, config.thresholdOrigin().name());
        }
        return rate;
    }

    private void appendContractRef(StringBuilder sb, ConfigurationData config) {
        if (config.isNormativeThreshold() && config.hasContractRef()) {
            sb.append(PUnitReporter.labelValueLn("Contract:", config.contractRef()));
        }
    }
}

