package org.javai.punit.model;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Records the provenance of a baseline used in a probabilistic test.
 *
 * <p>This information is included in test reports to provide traceability
 * back to the baseline that informed the test's statistical thresholds.
 *
 * @param filename the baseline filename
 * @param footprint the baseline's footprint hash
 * @param generatedAt when the baseline was generated
 * @param samples the number of samples in the baseline
 * @param covariateProfile the baseline's covariate profile
 */
public record BaselineProvenance(
        String filename,
        String footprint,
        Instant generatedAt,
        int samples,
        CovariateProfile covariateProfile
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public BaselineProvenance {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(footprint, "footprint must not be null");
    }

    /**
     * Returns a human-readable description of this provenance.
     *
     * @return formatted description
     */
    public String toHumanReadable() {
        var sb = new StringBuilder();
        sb.append(filename);
        if (generatedAt != null) {
            sb.append(" (generated ").append(DATE_FORMATTER.format(generatedAt));
        }
        sb.append(", ").append(samples).append(" samples)");
        return sb.toString();
    }

    /**
     * Returns properties suitable for inclusion in a test report.
     *
     * @return map of property names to values
     */
    public Map<String, String> toReportProperties() {
        var props = new LinkedHashMap<String, String>();
        props.put("punit.baseline.filename", filename);
        props.put("punit.baseline.footprint", footprint);
        props.put("punit.baseline.samples", String.valueOf(samples));
        if (generatedAt != null) {
            props.put("punit.baseline.generatedAt", DATE_FORMATTER.format(generatedAt));
        }
        if (covariateProfile != null && !covariateProfile.isEmpty()) {
            for (String key : covariateProfile.orderedKeys()) {
                var value = covariateProfile.get(key);
                props.put("punit.baseline.covariate." + key, value.toCanonicalString());
            }
        }
        return props;
    }
}

