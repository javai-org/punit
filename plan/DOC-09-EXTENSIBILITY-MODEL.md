# Extensibility Model

## 8.1 Pluggable Backends

Backends provide domain-specific execution context via SPI:

```java
public interface ExperimentBackend {
    
    /** Returns the backend identifier (e.g., "llm", "sensor"). */
    String getId();
    
    /** Builds a UseCaseContext from annotation parameters. */
    UseCaseContext buildContext(Map<String, String> parameters);
    
    /** Validates that required parameters are present. */
    void validateParameters(Map<String, String> parameters) 
        throws IllegalArgumentException;
    
    /** Returns parameter documentation for this backend. */
    Map<String, ParameterDoc> getParameterDocumentation();
}
```

### 8.1.1 Example: LLM Backend

```java
public class LlmExperimentBackend implements ExperimentBackend {
    
    @Override
    public String getId() { return "llm"; }
    
    @Override
    public UseCaseContext buildContext(Map<String, String> parameters) {
        String model = parameters.getOrDefault("model", "gpt-4");
        double temperature = Double.parseDouble(
            parameters.getOrDefault("temperature", "0.7"));
        String provider = parameters.getOrDefault("provider", "openai");
        
        return new LlmUseCaseContext(model, temperature, provider);
    }
}
```

## 8.2 Backend Registration

Backends are discovered via ServiceLoader:

```
META-INF/services/org.javai.punit.experiment.spi.ExperimentBackend
```

Contents:
```
org.javai.punit.backend.llm.LlmExperimentBackend
org.javai.punit.backend.sensor.SensorExperimentBackend
```

## 8.3 Core Remains Clean

The punit core:
- Defines the `ExperimentBackend` SPI
- Provides a default "generic" backend that passes parameters through unchanged
- Has no compile-time dependency on any specific backend

LLM-specific code lives entirely in `punit-backend-llm`.

## 8.4 Supporting Future Stochastic Domains

The abstraction supports any domain where:
1. Execution produces variable outcomes
2. Statistical aggregation is meaningful
3. Empirical thresholds can be established

Examples:
- **Distributed systems**: Network partitions, leader election, consensus
- **Hardware sensors**: Noise tolerance, calibration drift
- **Randomized algorithms**: Probabilistic data structures, Monte Carlo methods
- **External APIs**: Rate limits, transient failures, SLA compliance

---

*Previous: [Execution & Reporting Semantics](./DOC-08-EXECUTION-REPORTING.md)*

*Next: [Out of Scope Clarifications](./DOC-10-OUT-OF-SCOPE.md)*

*[Back to Table of Contents](./DOC-00-TOC.md)*
