# Plan: Transparent Statistics Mode

## Overview

Add a switch (command-line qualifier or system property) that enables detailed statistical explanations of test verdicts. This mode is designed for:

1. **Proof of Work**: Stakeholders who need rigorous evidence that testing meets statistical standards
2. **Educational**: Developers learning to understand the statistical methods underlying probabilistic testing
3. **Auditing**: Compliance scenarios requiring documented statistical methodology

## Requirement

When enabled, the framework should explain the verdict reached at the end of a test run in professional statistical terms, including:

- The hypothesis being tested
- The statistical test applied
- Confidence levels and intervals
- Sample sizes and their justification
- Pass/fail threshold derivation
- Margin of error and its implications

## Configuration

### Option 1: System Property
```bash
./gradlew test -Dpunit.stats.transparent=true
```

### Option 2: Environment Variable
```bash
PUNIT_STATS_TRANSPARENT=true ./gradlew test
```

### Option 3: Annotation Override
```java
@ProbabilisticTest(samples = 100, transparentStats = true)
void myTest() { ... }
```

## Example Output

### Standard Mode (current)
```
✓ shouldReturnValidJson: PASSED (87/100 successes, 87% ≥ 85% threshold)
```

### Transparent Mode
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

## Implementation Phases

### Phase 1: Core Infrastructure
- [ ] Create `TransparentStatsConfig` to manage enabled state
- [ ] Create `StatisticalExplanationBuilder` for generating explanations
- [ ] Define vocabulary constants (H₀, H₁, p-value, etc.)
- [ ] Add system property / env var detection

### Phase 2: Explanation Content
- [ ] Implement hypothesis statement generation
- [ ] Implement observed data summary
- [ ] Implement baseline reference formatting
- [ ] Implement statistical inference calculations display
- [ ] Implement verdict interpretation

### Phase 3: Integration
- [ ] Integrate with `ProbabilisticTestExtension` verdict reporting
- [ ] Add annotation attribute `transparentStats`
- [ ] Ensure output works with both console and structured reporters

### Phase 4: Documentation
- [ ] Add section to USER-GUIDE.md explaining the feature
- [ ] Document the statistical methods in STATISTICAL-COMPANION.md
- [ ] Add examples showing when to use this mode

## Output Formatting

The transparent stats output should:

1. **Be clearly delineated** from normal test output (box drawing, separators)
2. **Use proper mathematical notation** where possible (π, √, ≤)
3. **Include plain-English interpretations** alongside formulas
4. **Be structured consistently** across all test types
5. **Support machine-readable format** (optional JSON/YAML export)

## Dependencies

- Requires access to baseline/spec data for threshold derivation explanation
- Requires the statistical calculations already present in `ThresholdDeriver`
- May benefit from the pacing feature (for timing/budget explanations)

## Open Questions

1. Should transparent mode slow down execution to allow reading output in real-time?
2. Should there be a "summary only" vs "full detail" sub-mode?
3. Should the output be logged to a separate file for audit purposes?
4. How should partial results (early termination) be explained?

## Success Criteria

- [ ] A user can enable transparent mode via system property
- [ ] Every probabilistic test verdict includes full statistical explanation
- [ ] Explanations are accurate and use correct statistical terminology
- [ ] Non-statisticians can understand the plain-English interpretations
- [ ] Statisticians can verify the methodology from the output

