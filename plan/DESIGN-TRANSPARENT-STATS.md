# Transparent Statistics Mode Design

## 1. Motivation

Probabilistic testing makes statistical decisions that determine pass/fail verdicts. These decisions involve:

- Hypothesis formulation
- Threshold derivation from empirical baselines
- Confidence interval computation
- Sample size considerations

For most users, a simple "PASSED (87% ≥ 85%)" suffices. However, certain audiences need more:

| Audience                   | Need                                                             |
|----------------------------|------------------------------------------------------------------|
| **Auditors**               | Documented proof that testing methodology is statistically sound |
| **Skeptical stakeholders** | Evidence that AI system reliability claims are justified         |
| **New team members**       | Understanding of how PUnit reaches its verdicts                  |
| **Regulators**             | Compliance documentation for AI system validation                |

Transparent Statistics Mode addresses these needs by exposing the statistical reasoning behind every verdict.

## 2. Design Principles

### 2.1 Opt-In, Not Default

Transparent mode produces verbose output. It should:
- Be disabled by default
- Require explicit activation
- Not affect test execution semantics (only reporting)

### 2.2 Accurate, Not Simplified

When enabled, the output must be:
- Mathematically correct
- Using proper statistical terminology
- Reproducible (same inputs → same explanation)

### 2.3 Dual-Audience Writing

Each explanation should serve two audiences simultaneously:
- **Statisticians**: Formulas, notation, technical terms
- **Non-statisticians**: Plain English interpretations

### 2.4 Self-Contained

Each test's explanation should be complete without requiring external references. Include:
- The baseline source and its provenance
- All intermediate calculations
- The final verdict logic

## 3. Configuration Hierarchy

Configuration follows a precedence order (highest to lowest):

```
1. @ProbabilisticTest(transparentStats = true)   ← Per-test override
2. -Dpunit.stats.transparent=true                ← System property
3. PUNIT_STATS_TRANSPARENT=true                  ← Environment variable
4. punit.properties: stats.transparent=true      ← Config file
5. Default: false
```

### 3.1 Configuration Record

```java
public record TransparentStatsConfig(
    boolean enabled,
    DetailLevel detailLevel,
    OutputFormat format
) {
    public enum DetailLevel {
        SUMMARY,    // Verdict + key numbers only
        STANDARD,   // Full explanation (default when enabled)
        VERBOSE     // Includes power analysis, sensitivity discussion
    }
    
    public enum OutputFormat {
        CONSOLE,    // Human-readable with box drawing
        MARKDOWN,   // For embedding in reports
        JSON        // Machine-readable for tooling
    }
    
    public static TransparentStatsConfig resolve() {
        // Check annotation, system prop, env var, config file
    }
}
```

## 4. Explanation Structure

Every transparent stats explanation follows a consistent structure:

### 4.1 Sections

```
┌─────────────────────────────────────────────────────────────────┐
│ STATISTICAL ANALYSIS: {testName}                                │
├─────────────────────────────────────────────────────────────────┤
│ 1. HYPOTHESIS TEST                                              │
│    - Null hypothesis (H₀)                                       │
│    - Alternative hypothesis (H₁)                                │
│    - Test type                                                  │
├─────────────────────────────────────────────────────────────────┤
│ 2. OBSERVED DATA                                                │
│    - Sample size, successes, observed rate                      │
├─────────────────────────────────────────────────────────────────┤
│ 3. BASELINE REFERENCE                                           │
│    - Source spec file                                           │
│    - Empirical basis (samples, rate)                            │
│    - Threshold derivation method                                │
├─────────────────────────────────────────────────────────────────┤
│ 4. STATISTICAL INFERENCE                                        │
│    - Standard error calculation                                 │
│    - Confidence interval                                        │
│    - Test statistic (if applicable)                             │
│    - p-value (if applicable)                                    │
├─────────────────────────────────────────────────────────────────┤
│ 5. VERDICT                                                      │
│    - Result (PASS/FAIL)                                         │
│    - Plain English interpretation                               │
│    - Caveats and limitations                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Section Details

#### Hypothesis Test Section

The hypothesis framing depends on the operational approach:

| Approach               | H₀ (Null)                      | H₁ (Alternative)             |
|------------------------|--------------------------------|------------------------------|
| `MATCH_BASELINE`       | π = π₀ (performance unchanged) | π ≠ π₀ (performance changed) |
| `GUARD_LOWER_BOUND`    | π ≤ threshold                  | π > threshold                |
| `REGRESSION_DETECTION` | π ≥ π₀ - margin                | π < π₀ - margin              |

#### Observed Data Section

Simple factual reporting:
```
Sample size (n):     100
Successes (k):       87
Observed rate (p̂):   0.870
```

#### Baseline Reference Section

Traces the threshold back to its empirical source:
```
Source:              ShoppingUseCase.yaml
Generated:           2026-01-10T10:30:00Z
Experiment:          ShoppingExperiment.measureBaseline
Empirical basis:     1000 samples, 872 successes (87.2%)
Threshold derivation: 
  - Baseline rate: 87.2%
  - Standard error: 1.06%
  - 95% CI lower bound: 85.1%
  - Applied threshold: 85% (rounded)
```

#### Statistical Inference Section

Shows the mathematics:
```
Standard error:      SE = √(p̂(1-p̂)/n) = √(0.87 × 0.13 / 100) = 0.0336

95% Confidence interval:
  Lower: p̂ - 1.96 × SE = 0.87 - 0.066 = 0.804
  Upper: p̂ + 1.96 × SE = 0.87 + 0.066 = 0.936
  Interval: [80.4%, 93.6%]
```

#### Verdict Section

Combines technical result with interpretation:
```
Result:              PASS

Interpretation:      
  The observed success rate of 87.0% exceeds the required threshold 
  of 85.0%. The 95% confidence interval [80.4%, 93.6%] indicates 
  that we can be reasonably confident the true success rate is 
  above 80%, though the lower bound approaches the threshold.

Caveat:              
  With n=100 samples, a true drop from 87% to 84% would only be 
  detected approximately 40% of the time. For safety-critical 
  applications, consider increasing sample size.
```

### 4.3 Complete Example Output

When transparent mode is enabled, the console output presents all sections as a cohesive, professionally formatted report:

```
══════════════════════════════════════════════════════════════════════════════
STATISTICAL ANALYSIS: shouldReturnValidJson
══════════════════════════════════════════════════════════════════════════════

HYPOTHESIS TEST
  H₀ (null):        True success rate π ≤ 0.85 (system does not meet spec)
  H₁ (alternative): True success rate π > 0.85 (system meets spec)
  Test type:        One-sided binomial proportion test

OBSERVED DATA
  Sample size (n):     100
  Successes (k):       87
  Observed rate (p̂):   0.870

BASELINE REFERENCE
  Source:              ShoppingUseCase.yaml (generated 2026-01-10)
  Empirical basis:     1000 samples, 872 successes (87.2%)
  Threshold derivation: Lower bound of 95% CI = 85.1%, rounded to 85%

STATISTICAL INFERENCE
  Standard error:      SE = √(p̂(1-p̂)/n) = √(0.87 × 0.13 / 100) = 0.0336
  95% Confidence interval: [0.804, 0.936]
  
  Test statistic:      z = (p̂ - π₀) / √(π₀(1-π₀)/n)
                       z = (0.87 - 0.85) / √(0.85 × 0.15 / 100)
                       z = 0.56
  
  p-value:             P(Z > 0.56) = 0.288

VERDICT
  Result:              PASS
  Interpretation:      The observed success rate of 87% is consistent with 
                       the baseline expectation of 87.2%. The 95% confidence 
                       interval [80.4%, 93.6%] contains the threshold of 85%.
                       
  Caveat:              With n=100 samples, we can detect a drop from 87% to 
                       below 85% with approximately 50% power. For higher 
                       sensitivity, consider increasing sample size.

══════════════════════════════════════════════════════════════════════════════
```

This output achieves several goals:

1. **Visual clarity** — Double-line box drawing clearly delineates the statistical analysis from surrounding test output
2. **Mathematical precision** — Proper symbols (π, √, p̂, H₀, H₁) convey rigor
3. **Dual-audience accessibility** — Formulas satisfy statisticians; plain English interpretations serve everyone else
4. **Complete traceability** — The baseline source, derivation method, and all calculations are visible
5. **Actionable guidance** — The caveat provides concrete recommendations for improving sensitivity

## 5. Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ProbabilisticTestExtension                    │
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │ Test Runner  │───▶│   Verdict    │───▶│ VerdictReporter  │  │
│  └──────────────┘    │  Evaluator   │    └────────┬─────────┘  │
│                      └──────────────┘             │             │
└───────────────────────────────────────────────────┼─────────────┘
                                                    │
                                                    ▼
                              ┌─────────────────────────────────┐
                              │    TransparentStatsReporter     │
                              │                                 │
                              │  ┌───────────────────────────┐  │
                              │  │ StatisticalExplanation    │  │
                              │  │   Builder                 │  │
                              │  └───────────────────────────┘  │
                              │                                 │
                              │  ┌───────────────────────────┐  │
                              │  │ ExplanationRenderer       │  │
                              │  │   (Console/MD/JSON)       │  │
                              │  └───────────────────────────┘  │
                              └─────────────────────────────────┘
```

### 5.1 Key Classes

#### `StatisticalExplanation`

Immutable data class holding all explanation components:

```java
public record StatisticalExplanation(
    String testName,
    HypothesisStatement hypothesis,
    ObservedData observed,
    BaselineReference baseline,
    StatisticalInference inference,
    VerdictInterpretation verdict
) {
    public record HypothesisStatement(
        String nullHypothesis,
        String alternativeHypothesis,
        String testType
    ) {}
    
    public record ObservedData(
        int sampleSize,
        int successes,
        double observedRate
    ) {}
    
    public record BaselineReference(
        String sourceFile,
        Instant generatedAt,
        int baselineSamples,
        int baselineSuccesses,
        double baselineRate,
        String thresholdDerivation
    ) {}
    
    public record StatisticalInference(
        double standardError,
        double ciLower,
        double ciUpper,
        double confidenceLevel,
        Double testStatistic,  // nullable
        Double pValue          // nullable
    ) {}
    
    public record VerdictInterpretation(
        boolean passed,
        String technicalResult,
        String plainEnglish,
        List<String> caveats
    ) {}
}
```

#### `StatisticalExplanationBuilder`

Constructs explanations from test context:

```java
public class StatisticalExplanationBuilder {
    
    public StatisticalExplanation build(
            String testName,
            int samples,
            int successes,
            ExecutionSpecification spec,
            double threshold,
            boolean passed) {
        
        return new StatisticalExplanation(
            testName,
            buildHypothesis(threshold),
            buildObservedData(samples, successes),
            buildBaselineReference(spec),
            buildInference(samples, successes),
            buildVerdict(passed, samples, successes, threshold, spec)
        );
    }
    
    // ... builder methods for each section
}
```

#### `ExplanationRenderer`

Formats explanations for output:

```java
public interface ExplanationRenderer {
    String render(StatisticalExplanation explanation);
}

public class ConsoleExplanationRenderer implements ExplanationRenderer {
    // Box drawing, Unicode symbols, aligned columns
}

public class MarkdownExplanationRenderer implements ExplanationRenderer {
    // Headers, code blocks, tables
}

public class JsonExplanationRenderer implements ExplanationRenderer {
    // Structured JSON for tooling integration
}
```

## 6. Mathematical Notation

The renderer should use proper mathematical symbols where terminal capabilities allow:

| Concept                | Symbol | Fallback |
|------------------------|--------|----------|
| Sample proportion      | p̂     | p-hat    |
| Population proportion  | π      | pi       |
| Null hypothesis        | H₀     | H0       |
| Alternative hypothesis | H₁     | H1       |
| Less than or equal     | ≤      | <=       |
| Greater than or equal  | ≥      | >=       |
| Square root            | √      | sqrt     |
| Approximately          | ≈      | ~=       |

Detection of terminal Unicode support:
```java
boolean supportsUnicode = System.console() != null && 
    Charset.defaultCharset().name().toLowerCase().contains("utf");
```

## 7. Integration Points

### 7.1 With ProbabilisticTestExtension

After verdict determination, check if transparent mode is enabled:

```java
// In ProbabilisticTestExtension.afterTestExecution()
if (TransparentStatsConfig.resolve().enabled()) {
    StatisticalExplanation explanation = explanationBuilder.build(
        testName, samples, successes, spec, threshold, passed
    );
    
    String rendered = renderer.render(explanation);
    System.out.println(rendered);
    
    // Also publish as report entry for structured output
    context.publishReportEntry("punit.stats.explanation", 
        jsonRenderer.render(explanation));
}
```

### 7.2 With Experiment Extension

For MEASURE experiments, transparent mode could explain:
- Why the chosen sample size is statistically adequate
- How the baseline statistics were computed
- Confidence in the generated spec

### 7.3 With Pacing

When pacing causes early termination, the explanation should note:
- Samples actually executed vs planned
- Impact on statistical power
- Whether conclusions remain valid

## 8. Localization Considerations

While initial implementation is English-only, the design should support future localization:

```java
public interface ExplanationTextProvider {
    String hypothesisIntro();
    String nullHypothesisLabel();
    String observedDataIntro();
    // ... etc
}

public class EnglishExplanationText implements ExplanationTextProvider {
    // Default implementation
}
```

## 9. Testing Strategy

### 9.1 Unit Tests

- `StatisticalExplanationBuilderTest`: Verify correct formula application
- `ConsoleExplanationRendererTest`: Verify output formatting
- `TransparentStatsConfigTest`: Verify configuration precedence

### 9.2 Golden File Tests

Capture expected output for known inputs and compare:
```java
@Test
void explanationMatchesGoldenFile() {
    var explanation = builder.build("testCase", 100, 87, spec, 0.85, true);
    var rendered = renderer.render(explanation);
    
    assertThat(rendered).isEqualTo(readGoldenFile("expected-explanation.txt"));
}
```

### 9.3 Property-Based Tests

- Formulas produce mathematically correct results
- Confidence intervals contain observed proportion
- Explanations are internally consistent

## 10. Performance Considerations

Transparent mode adds overhead:
- String building and formatting
- Additional console I/O

This is acceptable because:
1. It's opt-in
2. It runs once per test (not per sample)
3. The value (understanding) justifies the cost

## 11. Future Extensions

### 11.1 Power Analysis

```
POWER ANALYSIS
  Detectable effect size: With n=100, you can detect a drop from 
  87% to below 77% with 80% power at α=0.05.
  
  Recommendation: For detecting a 5% drop (87% → 82%), increase 
  sample size to approximately 400.
```

### 11.2 Historical Comparison

```
TREND ANALYSIS
  Previous runs:  2026-01-08: 89%, 2026-01-09: 86%, Today: 87%
  Trend:          Stable (within normal variation)
```

### 11.3 Interactive Mode

A future CLI tool could allow drilling into specific calculations:
```
> punit explain ShoppingTest --section inference
```

## 12. References

- Implementation phases: See `PLAN-TRANSPARENT-STATS.md`
- Statistical methods: See `docs/STATISTICAL-COMPANION.md`
- Threshold derivation: See `ThresholdDeriver.java`

