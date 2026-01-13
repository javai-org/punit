package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.model.ExpirationPolicy;

/**
 * Writes empirical baselines to YAML files.
 *
 * <p>Generated specs include:
 * <ul>
 *   <li>{@code schemaVersion} - version identifier for spec format</li>
 *   <li>{@code contentFingerprint} - SHA-256 hash for integrity verification</li>
 * </ul>
 */
public class BaselineWriter {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String SCHEMA_VERSION = "punit-spec-1";
    
    /**
     * Writes a baseline to the specified path in YAML format.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void write(EmpiricalBaseline baseline, Path path) throws IOException {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(path, "path must not be null");
        
        // Ensure parent directories exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        String content = toYaml(baseline);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    
    /**
     * Writes a baseline to YAML format.
     *
     * <p>The generated YAML includes:
     * <ul>
     *   <li>{@code schemaVersion} - version identifier for spec format</li>
     *   <li>{@code contentFingerprint} - SHA-256 hash for integrity verification</li>
     * </ul>
     *
     * @param baseline the baseline
     * @return YAML string
     */
    public String toYaml(EmpiricalBaseline baseline) {
        // Build the content without fingerprint first
        String contentWithoutFingerprint = buildYamlContent(baseline);
        
        // Compute fingerprint of the content
        String fingerprint = computeFingerprint(contentWithoutFingerprint);
        
        // Build final content with fingerprint at the end
        // Note: contentWithoutFingerprint already ends with \n, so no extra newline needed
        StringBuilder sb = new StringBuilder(contentWithoutFingerprint);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Builds the YAML content (without the fingerprint line).
     */
    private String buildYamlContent(EmpiricalBaseline baseline) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Empirical Baseline for ").append(baseline.getUseCaseId()).append("\n");
        sb.append("# Generated automatically by punit experiment runner\n");
        sb.append("# DO NOT EDIT - create a specification based on this baseline instead\n\n");
        
        sb.append("schemaVersion: ").append(SCHEMA_VERSION).append("\n");
        sb.append("useCaseId: ").append(baseline.getUseCaseId()).append("\n");
        if (baseline.getExperimentId() != null) {
            sb.append("experimentId: ").append(baseline.getExperimentId()).append("\n");
        }
        sb.append("generatedAt: ").append(ISO_FORMATTER.format(baseline.getGeneratedAt())).append("\n");
        
        if (baseline.getExperimentClass() != null) {
            sb.append("experimentClass: ").append(baseline.getExperimentClass()).append("\n");
        }
        if (baseline.getExperimentMethod() != null) {
            sb.append("experimentMethod: ").append(baseline.getExperimentMethod()).append("\n");
        }
        
        // Execution
        sb.append("\nexecution:\n");
        sb.append("  samplesPlanned: ").append(baseline.getExecution().samplesPlanned()).append("\n");
        sb.append("  samplesExecuted: ").append(baseline.getExecution().samplesExecuted()).append("\n");
        sb.append("  terminationReason: ").append(baseline.getExecution().terminationReason()).append("\n");
        if (baseline.getExecution().terminationDetails() != null) {
            sb.append("  terminationDetails: ").append(baseline.getExecution().terminationDetails()).append("\n");
        }
        
        // Requirements (derived from empirical data)
        // minPassRate is set to the lower bound of the 95% confidence interval
        // This is statistically sound: if the true rate is at this bound, there's
        // a 95% chance the test will pass (given sufficient samples)
        double derivedMinPassRate = baseline.getStatistics().confidenceIntervalLower();
        sb.append("\nrequirements:\n");
        sb.append("  minPassRate: ").append(String.format("%.4f", derivedMinPassRate)).append("\n");
        
        // Statistics
        sb.append("\nstatistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: ").append(String.format("%.4f", baseline.getStatistics().observedSuccessRate())).append("\n");
        sb.append("    standardError: ").append(String.format("%.4f", baseline.getStatistics().standardError())).append("\n");
        sb.append("    confidenceInterval95: [")
            .append(String.format("%.4f", baseline.getStatistics().confidenceIntervalLower()))
            .append(", ")
            .append(String.format("%.4f", baseline.getStatistics().confidenceIntervalUpper()))
            .append("]\n");
        sb.append("  successes: ").append(baseline.getStatistics().successes()).append("\n");
        sb.append("  failures: ").append(baseline.getStatistics().failures()).append("\n");
        
        if (!baseline.getStatistics().failureDistribution().isEmpty()) {
            sb.append("  failureDistribution:\n");
            for (Map.Entry<String, Integer> entry : baseline.getStatistics().failureDistribution().entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        // Cost
        sb.append("\ncost:\n");
        sb.append("  totalTimeMs: ").append(baseline.getCost().totalTimeMs()).append("\n");
        sb.append("  avgTimePerSampleMs: ").append(baseline.getCost().avgTimePerSampleMs()).append("\n");
        sb.append("  totalTokens: ").append(baseline.getCost().totalTokens()).append("\n");
        sb.append("  avgTokensPerSample: ").append(baseline.getCost().avgTokensPerSample()).append("\n");
        
        // Success criteria
        if (baseline.getUseCaseCriteria() != null) {
            sb.append("\nsuccessCriteria:\n");
            sb.append("  definition: \"").append(escapeYamlString(baseline.getUseCaseCriteria())).append("\"\n");
        }
        
        // Result projections (EXPLORE mode only)
        if (baseline.hasResultProjections()) {
            sb.append("\nresultProjection:\n");
            
            for (ResultProjection projection : baseline.getResultProjections()) {
                sb.append("  sample[").append(projection.sampleIndex()).append("]:\n");
                sb.append("    executionTimeMs: ")
                  .append(projection.executionTimeMs())
                  .append("\n");
                sb.append("    diffableContent:\n");
                
                for (String line : projection.diffableLines()) {
                    sb.append("      - \"")
                      .append(escapeYamlString(line))
                      .append("\"\n");
                }
            }
        }

        // Expiration policy
        if (baseline.hasExpirationPolicy()) {
            ExpirationPolicy policy = baseline.getExpirationPolicy();
            sb.append("\nexpiration:\n");
            sb.append("  expiresInDays: ").append(policy.expiresInDays()).append("\n");
            sb.append("  baselineEndTime: ").append(ISO_FORMATTER.format(policy.baselineEndTime())).append("\n");
            policy.expirationTime().ifPresent(exp ->
                sb.append("  expirationDate: ").append(ISO_FORMATTER.format(exp)).append("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Computes a SHA-256 fingerprint of the content.
     */
    private String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private void appendYamlValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            if (needsYamlQuoting(str)) {
                sb.append("\"").append(escapeYamlString(str)).append("\"");
            } else {
                sb.append(str);
            }
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            sb.append("\"").append(escapeYamlString(value.toString())).append("\"");
        }
    }
    
    private boolean needsYamlQuoting(String str) {
        if (str.isEmpty()) return true;
        if (str.contains(":") || str.contains("#") || str.contains("\"") || 
            str.contains("'") || str.contains("\n") || str.contains("\r")) {
            return true;
        }
        // Check for YAML special values
        String lower = str.toLowerCase();
        return lower.equals("true") || lower.equals("false") || 
               lower.equals("null") || lower.equals("yes") || lower.equals("no");
    }
    
    private String escapeYamlString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
