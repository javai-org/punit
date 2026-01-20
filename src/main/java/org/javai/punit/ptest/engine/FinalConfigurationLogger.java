package org.javai.punit.ptest.engine;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.reporting.PUnitReporter;

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
            String contractRef
    ) {
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

        if (config.isNormativeThreshold()) {
            sb.append(PUnitReporter.labelValueLn("Mode:", config.thresholdOrigin().name() + "-DRIVEN"));
            if (config.hasSpecId()) {
                sb.append(PUnitReporter.labelValueLn("Use Case:", config.specId()));
            }
            sb.append(PUnitReporter.labelValueLn("Threshold:",
                    String.format("%.1f%% (%s)", config.minPassRate() * 100, config.thresholdOrigin().name())));
            if (config.hasContractRef()) {
                sb.append(PUnitReporter.labelValueLn("Contract:", config.contractRef()));
            }
        } else if (config.hasSpecId()) {
            sb.append(PUnitReporter.labelValueLn("Mode:", "SPEC-DRIVEN"));
            sb.append(PUnitReporter.labelValueLn("Spec:", config.specId()));
            sb.append(PUnitReporter.labelValueLn("Threshold:",
                    String.format("%.1f%% (derived from baseline)", config.minPassRate() * 100)));
        } else {
            sb.append(PUnitReporter.labelValueLn("Mode:", "EXPLICIT THRESHOLD"));
            String thresholdNote = "";
            if (config.hasThresholdOrigin()) {
                thresholdNote = " (" + config.thresholdOrigin().name() + ")";
            }
            sb.append(PUnitReporter.labelValueLn("Threshold:",
                    String.format("%.1f%%%s", config.minPassRate() * 100, thresholdNote)));
        }
        sb.append(PUnitReporter.labelValue("Samples:", String.valueOf(config.samples())));

        return sb.toString();
    }
}

