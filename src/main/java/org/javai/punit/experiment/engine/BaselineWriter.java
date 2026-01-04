package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.experiment.model.EmpiricalBaseline;

/**
 * Writes empirical baselines to files in YAML or JSON format.
 */
public class BaselineWriter {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    /**
     * Writes a baseline to the specified path.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @param format the output format ("yaml" or "json")
     * @throws IOException if writing fails
     */
    public void write(EmpiricalBaseline baseline, Path path, String format) throws IOException {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(path, "path must not be null");
        
        // Ensure parent directories exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        String content;
        if ("json".equalsIgnoreCase(format)) {
            content = toJson(baseline);
        } else {
            content = toYaml(baseline);
        }
        
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    
    /**
     * Writes a baseline to YAML format.
     *
     * @param baseline the baseline
     * @return YAML string
     */
    public String toYaml(EmpiricalBaseline baseline) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Empirical Baseline for ").append(baseline.getUseCaseId()).append("\n");
        sb.append("# Generated automatically by punit experiment runner\n");
        sb.append("# DO NOT EDIT - create a specification based on this baseline instead\n\n");
        
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
        
        // Context
        if (!baseline.getContext().isEmpty()) {
            sb.append("\ncontext:\n");
            for (Map.Entry<String, Object> entry : baseline.getContext().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ");
                appendYamlValue(sb, entry.getValue());
                sb.append("\n");
            }
        }
        
        // Execution
        sb.append("\nexecution:\n");
        sb.append("  samplesPlanned: ").append(baseline.getExecution().samplesPlanned()).append("\n");
        sb.append("  samplesExecuted: ").append(baseline.getExecution().samplesExecuted()).append("\n");
        sb.append("  terminationReason: ").append(baseline.getExecution().terminationReason()).append("\n");
        if (baseline.getExecution().terminationDetails() != null) {
            sb.append("  terminationDetails: ").append(baseline.getExecution().terminationDetails()).append("\n");
        }
        
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
        if (baseline.getSuccessCriteriaDefinition() != null) {
            sb.append("\nsuccessCriteria:\n");
            sb.append("  definition: \"").append(escapeYamlString(baseline.getSuccessCriteriaDefinition())).append("\"\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Writes a baseline to JSON format.
     *
     * @param baseline the baseline
     * @return JSON string
     */
    public String toJson(EmpiricalBaseline baseline) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        sb.append("  \"useCaseId\": \"").append(escapeJsonString(baseline.getUseCaseId())).append("\",\n");
        if (baseline.getExperimentId() != null) {
            sb.append("  \"experimentId\": \"").append(escapeJsonString(baseline.getExperimentId())).append("\",\n");
        }
        sb.append("  \"generatedAt\": \"").append(ISO_FORMATTER.format(baseline.getGeneratedAt())).append("\",\n");
        
        if (baseline.getExperimentClass() != null) {
            sb.append("  \"experimentClass\": \"").append(escapeJsonString(baseline.getExperimentClass())).append("\",\n");
        }
        if (baseline.getExperimentMethod() != null) {
            sb.append("  \"experimentMethod\": \"").append(escapeJsonString(baseline.getExperimentMethod())).append("\",\n");
        }
        
        // Context
        sb.append("  \"context\": {\n");
        int contextIdx = 0;
        for (Map.Entry<String, Object> entry : baseline.getContext().entrySet()) {
            sb.append("    \"").append(escapeJsonString(entry.getKey())).append("\": ");
            appendJsonValue(sb, entry.getValue());
            if (contextIdx < baseline.getContext().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            contextIdx++;
        }
        sb.append("  },\n");
        
        // Execution
        sb.append("  \"execution\": {\n");
        sb.append("    \"samplesPlanned\": ").append(baseline.getExecution().samplesPlanned()).append(",\n");
        sb.append("    \"samplesExecuted\": ").append(baseline.getExecution().samplesExecuted()).append(",\n");
        sb.append("    \"terminationReason\": \"").append(baseline.getExecution().terminationReason()).append("\"");
        if (baseline.getExecution().terminationDetails() != null) {
            sb.append(",\n    \"terminationDetails\": \"").append(escapeJsonString(baseline.getExecution().terminationDetails())).append("\"");
        }
        sb.append("\n  },\n");
        
        // Statistics
        sb.append("  \"statistics\": {\n");
        sb.append("    \"observedSuccessRate\": ").append(baseline.getStatistics().observedSuccessRate()).append(",\n");
        sb.append("    \"standardError\": ").append(baseline.getStatistics().standardError()).append(",\n");
        sb.append("    \"confidenceInterval95\": [")
            .append(baseline.getStatistics().confidenceIntervalLower())
            .append(", ")
            .append(baseline.getStatistics().confidenceIntervalUpper())
            .append("],\n");
        sb.append("    \"successes\": ").append(baseline.getStatistics().successes()).append(",\n");
        sb.append("    \"failures\": ").append(baseline.getStatistics().failures()).append(",\n");
        sb.append("    \"failureDistribution\": {\n");
        int failIdx = 0;
        for (Map.Entry<String, Integer> entry : baseline.getStatistics().failureDistribution().entrySet()) {
            sb.append("      \"").append(escapeJsonString(entry.getKey())).append("\": ").append(entry.getValue());
            if (failIdx < baseline.getStatistics().failureDistribution().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            failIdx++;
        }
        sb.append("    }\n");
        sb.append("  },\n");
        
        // Cost
        sb.append("  \"cost\": {\n");
        sb.append("    \"totalTimeMs\": ").append(baseline.getCost().totalTimeMs()).append(",\n");
        sb.append("    \"avgTimePerSampleMs\": ").append(baseline.getCost().avgTimePerSampleMs()).append(",\n");
        sb.append("    \"totalTokens\": ").append(baseline.getCost().totalTokens()).append(",\n");
        sb.append("    \"avgTokensPerSample\": ").append(baseline.getCost().avgTokensPerSample()).append("\n");
        sb.append("  }");
        
        // Success criteria
        if (baseline.getSuccessCriteriaDefinition() != null) {
            sb.append(",\n  \"successCriteria\": {\n");
            sb.append("    \"definition\": \"").append(escapeJsonString(baseline.getSuccessCriteriaDefinition())).append("\"\n");
            sb.append("  }");
        }
        
        sb.append("\n}\n");
        return sb.toString();
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
    
    private void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJsonString((String) value)).append("\"");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            sb.append("\"").append(escapeJsonString(value.toString())).append("\"");
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
    
    private String escapeJsonString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}

