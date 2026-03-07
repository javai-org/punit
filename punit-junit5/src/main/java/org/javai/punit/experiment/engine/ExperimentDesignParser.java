package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.punit.api.Config;
import org.javai.punit.api.ExperimentDesign;
import org.javai.punit.experiment.model.ExperimentConfig;

/**
 * Parses experiment designs from annotations or YAML files.
 */
public class ExperimentDesignParser {
    
    private static final Pattern YAML_KEY_VALUE = Pattern.compile("^\\s*(\\w+)\\s*:\\s*(.+?)\\s*$");
    
    /**
     * Parses an ExperimentDesign from annotation.
     *
     * @param design the annotation
     * @return list of parsed configs
     */
    public List<ExperimentConfig> parse(ExperimentDesign design) {
        List<ExperimentConfig> configs = new ArrayList<>();
        Config[] configAnnotations = design.value();
        
        for (int i = 0; i < configAnnotations.length; i++) {
            configs.add(parseConfig(configAnnotations[i], i));
        }
        
        return configs;
    }
    
    /**
     * Parses a single Config annotation.
     *
     * @param config the annotation
     * @param index the config index
     * @return the parsed config
     */
    public ExperimentConfig parseConfig(Config config, int index) {
        ExperimentConfig.Builder builder = ExperimentConfig.builder()
            .index(index);
        
        if (config.name() != null && !config.name().isEmpty()) {
            builder.name(config.name());
        }
        
        // Parse convenience attributes
        if (config.model() != null && !config.model().isEmpty()) {
            builder.factor("model", config.model());
        }
        if (!Double.isNaN(config.temperature())) {
            builder.factor("temperature", config.temperature());
        }
        if (config.maxTokens() >= 0) {
            builder.factor("maxTokens", config.maxTokens());
        }
        
        // Parse params array
        for (String param : config.params()) {
            int eqIdx = param.indexOf('=');
            if (eqIdx > 0) {
                String key = param.substring(0, eqIdx).trim();
                String value = param.substring(eqIdx + 1).trim();
                builder.factor(key, parseValue(value));
            }
        }
        
        return builder.build();
    }
    
    /**
     * Parses an experiment design from a YAML file.
     *
     * @param yamlPath the path to the YAML file
     * @return the parsed design
     * @throws IOException if reading fails
     */
    public ParsedDesign parseYaml(Path yamlPath) throws IOException {
        String content = Files.readString(yamlPath);
        return parseYamlContent(content);
    }
    
    /**
     * Parses an experiment design from YAML content.
     *
     * @param yamlContent the YAML content
     * @return the parsed design
     */
    public ParsedDesign parseYamlContent(String yamlContent) {
        ParsedDesign design = new ParsedDesign();
        
        String[] lines = yamlContent.split("\n");
        boolean inConfigs = false;
        boolean inGoal = false;
        boolean inBudgets = false;
        boolean inContext = false;
        ExperimentConfig.Builder currentConfig = null;
        int configIndex = 0;
        
        for (String line : lines) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            
            // Top-level keys
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                inConfigs = false;
                inGoal = false;
                inBudgets = false;
                inContext = false;
                
                if (line.startsWith("experimentId:")) {
                    design.experimentId = extractValue(line);
                } else if (line.startsWith("useCaseId:")) {
                    design.useCaseId = extractValue(line);
                } else if (line.startsWith("samplesPerConfig:")) {
                    design.samplesPerConfig = Integer.parseInt(extractValue(line));
                } else if (line.startsWith("configs:")) {
                    inConfigs = true;
                } else if (line.startsWith("goal:")) {
                    inGoal = true;
                } else if (line.startsWith("budgets:")) {
                    inBudgets = true;
                } else if (line.startsWith("context:")) {
                    inContext = true;
                }
                continue;
            }
            
            // Nested content
            String trimmed = line.trim();
            
            if (inConfigs) {
                if (trimmed.startsWith("- ")) {
                    // New config
                    if (currentConfig != null) {
                        design.configs.add(currentConfig.build());
                    }
                    currentConfig = ExperimentConfig.builder().index(configIndex++);
                    
                    // Parse first key-value on same line
                    String rest = trimmed.substring(2).trim();
                    parseConfigLine(currentConfig, rest);
                } else if (currentConfig != null) {
                    // Continuation of current config
                    parseConfigLine(currentConfig, trimmed);
                }
            } else if (inGoal) {
                if (trimmed.startsWith("successRate:")) {
                    String value = extractValue(trimmed);
                    design.goalSuccessRate = parseGoalValue(value);
                } else if (trimmed.startsWith("maxLatencyMs:")) {
                    design.goalMaxLatencyMs = Long.parseLong(extractValue(trimmed));
                } else if (trimmed.startsWith("maxTokensPerSample:")) {
                    design.goalMaxTokensPerSample = Long.parseLong(extractValue(trimmed));
                }
            } else if (inBudgets) {
                if (trimmed.startsWith("timeBudgetMs:")) {
                    design.timeBudgetMs = Long.parseLong(extractValue(trimmed));
                } else if (trimmed.startsWith("tokenBudget:")) {
                    design.tokenBudget = Long.parseLong(extractValue(trimmed));
                }
            } else if (inContext) {
                Matcher m = YAML_KEY_VALUE.matcher(trimmed);
                if (m.matches()) {
                    design.context.put(m.group(1), parseValue(m.group(2)));
                }
            }
        }
        
        // Add last config
        if (currentConfig != null) {
            design.configs.add(currentConfig.build());
        }
        
        return design;
    }
    
    private void parseConfigLine(ExperimentConfig.Builder builder, String line) {
        Matcher m = YAML_KEY_VALUE.matcher(line);
        if (m.matches()) {
            builder.factor(m.group(1), parseValue(m.group(2)));
        }
    }
    
    private String extractValue(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return "";
        String value = line.substring(colonIdx + 1).trim();
        // Remove quotes if present
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
    
    private double parseGoalValue(String value) {
        // Handle expressions like ">= 0.90"
        value = value.replaceAll("[>=<]", "").trim();
        return Double.parseDouble(value);
    }
    
    private Object parseValue(String value) {
        // Remove quotes if present
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        
        // Try to parse as number or boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    /**
     * Parsed experiment design from YAML.
     */
    public static class ParsedDesign {
        public String experimentId;
        public String useCaseId;
        public int samplesPerConfig = 100;
        public long timeBudgetMs = 0;
        public long tokenBudget = 0;
        public double goalSuccessRate = Double.NaN;
        public long goalMaxLatencyMs = Long.MAX_VALUE;
        public long goalMaxTokensPerSample = Long.MAX_VALUE;
        public Map<String, Object> context = new LinkedHashMap<>();
        public List<ExperimentConfig> configs = new ArrayList<>();
    }
}

