# Experiment-to-Specification Pass Rate Derivation

## Executive Summary

This document describes a **statistically rigorous approach** for deriving minimum pass rate thresholds for probabilistic regression tests from experimental baselines. The core insight is that **reducing sample size increases sampling variance**, so a test with fewer samples cannot use the same raw pass rate observed in a larger experiment.

**The Problem**: An experiment runs 1000 samples and observes 95.1% success rate. If the regression test runs only 100 samples with a 95.1% threshold, normal sampling variance will cause false failures—the test may legitimately see 93% or 91% due to chance alone, even though the underlying system hasn't degraded.

**The Solution**: Derive a **one-sided lower confidence bound** on the observed success probability that accounts for the increased variance in smaller test samples. We use a one-sided bound because regression testing is concerned with detecting **degradation** (pass rate falling below acceptable levels), not with detecting improvements. This ensures the test is statistically consistent with the experimental observations while maintaining sensitivity to quality regressions.

---

## 1. Motivation and Problem Statement

### 1.1 The Fundamental Tension

Punit models test executions as **Bernoulli trials** where each sample either passes (1) or fails (0). The outcomes follow a **binomial distribution** with underlying success probability *p*.

| Phase | Sample Size | Purpose | Variance |
|-------|-------------|---------|----------|
| **Experiment** | Large (e.g., 1000) | High-precision estimate of *p* | Low |
| **Regression Test** | Small (e.g., 100) | Cost-efficient CI gating | High |

The experiment provides a point estimate p̂_exp, but using this directly as the test threshold ignores the statistical reality: **smaller samples have higher variance**.

### 1.2 Illustrative Example

**Experiment Phase:**
- Samples: 1000
- Successes: 951
- Observed rate: p̂_exp = 0.951

**Regression Test Phase:**
- Samples: 100
- Expected successes if true rate is 0.951: ~95
- Standard error: SE = √(0.951 × 0.049 / 100) ≈ 0.0216

With a standard error of ~2.2%, normal sampling variation means the observed rate in 100 samples could easily range from ~90.6% to ~99.4% (±2 standard errors) even when the underlying system hasn't changed.

**Problem**: Setting `minPassRate = 0.951` for the 100-sample test would cause false failures whenever the observed rate dips below 95.1% due to random sampling—which happens frequently.

**Solution**: Derive a **statistically equivalent threshold** that accounts for the increased variance. For this example, a threshold around 90.7% (using a 95% one-sided confidence bound) would be appropriate.

### 1.3 Core Principle

> **A deterministic acceptance rule must be derived from prior empirical evidence in a way that is statistically consistent with the observed population parameter, while accounting for the increased sampling variance of smaller test runs.**

### 1.4 Economic Pressure on Test Sample Sizes

**A critical design constraint**: Regression tests run frequently—on every commit, pull request, or scheduled CI job. This creates strong economic pressure to minimize sample sizes:

| Cost Factor | Impact of Large Sample Sizes |
|-------------|------------------------------|
| **LLM API costs** | Direct $ cost per token/call |
| **CI pipeline duration** | Longer feedback loops, blocked developers |
| **Resource consumption** | Compute, memory, network bandwidth |
| **Rate limits** | May hit API throttling with large batches |

**The tension**: Smaller samples are cheaper but have higher variance, making thresholds less reliable. This is precisely why the statistical machinery in this document matters—it allows us to use **economically viable sample sizes** while maintaining **statistical rigor**.

**Practical implications**:

1. **Small test samples are expected, not exceptional**: Regression tests with n=50 or n=100 are the norm, not edge cases. The framework must handle these well.

2. **Wilson intervals become essential**: With n < 40 being common for cost reasons, the Wilson score method isn't an obscure fallback—it's a primary tool.

3. **Method selection must be automatic**: Users facing cost pressure will choose small sample sizes. The framework must transparently select the appropriate statistical method without user intervention.

4. **Guidance on minimum viable sample sizes**: The framework should warn when sample sizes are too small for reliable statistical inference, while respecting the economic constraints.

> **Design principle**: The framework should make it easy to do the statistically correct thing at any sample size users choose for economic reasons.

### 1.5 Why One-Sided Bounds Only

Regression testing has an **asymmetric concern**:

| Direction | Concern Level | Action Required |
|-----------|---------------|-----------------|
| **Pass rate decreasing** | High | Block CI, investigate regression |
| **Pass rate increasing** | None | Allow to pass, no action needed |

We don't care if a test performs *better* than the experimental baseline—that's good news requiring no intervention. We only care about *degradation*.

Therefore, we use **one-sided lower confidence bounds** exclusively:

1. **One-sided lower bound**: "We are 95% confident the true rate is **at least** p_lower"
2. **Not two-sided**: We don't need "We are 95% confident the true rate is **between** p_lower and p_upper"

This focus on one-sided bounds:
- Maximizes statistical power for detecting degradation
- Avoids unnecessary conservatism from protecting against both directions
- Aligns with the purpose of regression testing (gatekeeping against quality loss)

---

## 2. Statistical Framework

### 2.1 Notation

| Symbol | Description |
|--------|-------------|
| *p* | True (unknown) success probability |
| n_exp | Experiment sample size |
| k_exp | Experiment successes observed |
| p̂_exp | Experiment point estimate: k_exp / n_exp |
| n_test | Regression test sample size |
| SE_test | Standard error for test sample: √(p̂_exp(1-p̂_exp)/n_test) |
| z_α | Z-score for **one-sided** confidence level (1-α). For 95% confidence, z_α = 1.645 |
| p_lower | **One-sided** lower confidence bound, used as the regression test threshold |

**Note on z_α**: For one-sided bounds, we use the z-score corresponding to the full α in one tail. This differs from two-sided intervals which use z_{α/2}. For example:
- One-sided 95% confidence: z = 1.645 (5% in lower tail)
- Two-sided 95% confidence: z = 1.96 (2.5% in each tail)

### 2.2 Derivation of One-Sided Lower Confidence Bound

**Why One-Sided?**

Regression testing is asymmetric in its concerns:
- **We care about degradation**: If the pass rate falls significantly below the experimental baseline, that's a problem requiring investigation.
- **We don't care about improvement**: If the pass rate exceeds the baseline, that's fine—no action needed.

A **two-sided** confidence interval would protect against both directions, but that's wasteful for our purpose. By using a **one-sided lower bound**, we:
1. Maximize statistical power for detecting degradation
2. Accept that we won't detect unexpected improvements (which is fine)
3. Use a smaller critical value (z_α instead of z_{α/2}), resulting in a less conservative threshold

Given:
- Experiment observed p̂_exp from n_exp samples
- Regression test will use n_test samples (typically n_test < n_exp)

The **standard error for the test sample** is:

```
SE_test = √(p̂_exp × (1 - p̂_exp) / n_test)
```

The **one-sided lower confidence bound** at confidence level (1-α) is:

```
p_lower = p̂_exp - z_α × SE_test
```

**Note**: For one-sided bounds, we use z_α (not z_{α/2} as in two-sided intervals). This gives us a less conservative threshold while maintaining the desired confidence for detecting degradation.

Common z-scores for **one-sided** intervals:
| Confidence Level | z_α | Meaning |
|-----------------|-----|---------|
| 90% | 1.282 | 10% chance of false alarm |
| 95% | 1.645 | 5% chance of false alarm |
| 99% | 2.326 | 1% chance of false alarm |

### 2.3 Worked Example

**Given:**
- n_exp = 1000, k_exp = 951, p̂_exp = 0.951
- n_test = 100
- Desired confidence: 95%

**Calculation:**
```
SE_test = √(0.951 × 0.049 / 100)
        = √(0.0004660 / 100)
        = √0.000004660
        ≈ 0.0216

p_lower = 0.951 - 1.645 × 0.0216
        = 0.951 - 0.0355
        ≈ 0.9155
```

**Result**: The regression test with 100 samples should use `minPassRate = 0.916` (rounded) to be statistically consistent with the 95.1% observed in the experiment.

### 2.4 Interpretation

The one-sided lower bound p_lower = 0.916 means:

**For passing tests:**
- If the observed rate ≥ 91.6%, we have no statistical evidence of degradation.
- The system may be performing at baseline, slightly below, or even above—we don't distinguish.
- Any rate ≥ 91.6% is "consistent with" the experimental baseline of 95.1%.

**For failing tests:**
- If the observed rate < 91.6%, we conclude (with 95% confidence) that the system has **degraded**.
- There is only a 5% chance this conclusion is wrong (false positive).
- The shortfall is too large to be explained by random sampling variance alone.

**What we intentionally ignore:**
- If observed rate is 98% (above baseline), the test still passes—no special action.
- We don't compute upper bounds because detecting "unexpected improvement" isn't useful for CI gating.

### 2.5 Alternative: Wilson Score Lower Bound

For small samples or extreme success rates (p̂ near 0 or 1), the normal approximation may be less accurate. The **Wilson score interval** provides a more robust one-sided lower bound:

```
p_lower = (p̂ + z²/2n - z√(p̂(1-p̂)/n + z²/4n²)) / (1 + z²/n)
```

Where:
- p̂ = observed success rate from experiment
- n = test sample size
- z = z-score for desired one-sided confidence level

**Note**: This formula gives the **lower bound only**, which is exactly what we need for regression testing. We are not computing an upper bound because we don't need to detect improvements.

### 2.6 When to Use Which Method

The choice between normal approximation and Wilson score depends on sample size and the observed success rate:

| Condition | Recommendation | Rationale |
|-----------|----------------|-----------|
| n ≥ 40 **and** p̂ not near 0 or 1 | Normal approximation OK | Central Limit Theorem provides good approximation |
| n ≥ 20 **but** p̂ near 0 or 1 | **Wilson preferred** | Normal approx has poor coverage at extremes |
| n < 20 | **Wilson strongly recommended** | Normal approx increasingly unreliable |
| n < 10 | **Normal approximation should NOT be used** | Wilson or exact methods required |

**Definition of "p̂ near 0 or 1"**: Typically p̂ < 0.1 or p̂ > 0.9, though some texts use p̂ < 0.05 or p̂ > 0.95 for the most extreme cases.

**Practical guidance for punit**:

In the context of punit regression tests:
- **Test sample sizes** (n_test) are typically 50–200 due to economic pressure (see Section 1.4)
- **Experimental success rates** (p̂_exp) are typically 0.80–0.99, which are "near 1" and favor Wilson
- **The combination is common**: n_test in the 50-100 range with p̂ > 0.90 is a typical scenario

**Recommendation**: Given that:
1. Economic pressure drives test sample sizes toward the lower end (n=50–100)
2. Success rates for production systems are typically high (p̂ > 0.85)
3. Wilson has no downside for larger samples

The framework should **default to Wilson score** for all threshold calculations, falling back to normal approximation only when explicitly requested or when conditions clearly favor it (n ≥ 40 and 0.1 ≤ p̂ ≤ 0.9).

**Why this matters**: The normal approximation can produce:
- Bounds outside [0, 1] for extreme rates (e.g., p̂ = 0.99 with small n)
- Poor coverage (actual confidence level differs from nominal)
- Overly optimistic or pessimistic thresholds

The Wilson bound remains well-behaved across all conditions and has near-nominal coverage even for edge cases.

---

## 3. Integration with Punit Framework

### 3.1 Current State: Specifications and Baselines

The existing punit flow is:

```
Experiment → EmpiricalBaseline → ExecutionSpecification → @ProbabilisticTest
```

Currently, the `ExecutionSpecification` contains:
- `minPassRate`: The threshold for test pass/fail
- This value is typically set manually by a human reviewing the baseline

**Gap**: There is no automated mechanism to compute a statistically appropriate `minPassRate` based on the test sample size.

### 3.2 Proposed Enhancement: Sample-Size-Aware Thresholds

The specification should include:
1. The **experimental basis** (sample size, observed rate, confidence interval)
2. The **intended test sample size**
3. The **derived threshold** appropriate for that test sample size

This derivation can be:
- **Automatic**: Framework computes p_lower when generating specs
- **Auditable**: Derivation parameters are recorded for human review
- **Validated**: Test execution verifies consistency with spec

### 3.3 New Model: `RegressionThreshold`

```java
/**
 * Represents a statistically-derived minimum pass rate threshold for regression testing.
 * 
 * <p>The threshold is computed from experimental baseline data, adjusted for the
 * intended test sample size and desired confidence level.
 */
public record RegressionThreshold(
    /** The experimental basis for this threshold */
    ExperimentalBasis basis,
    
    /** The test configuration for which this threshold applies */
    TestConfiguration testConfig,
    
    /** The derived minimum pass rate for the test */
    double minPassRate,
    
    /** Metadata about the derivation */
    DerivationMetadata derivation
) {
    
    /**
     * Experimental data from which the threshold is derived.
     */
    public record ExperimentalBasis(
        /** Number of samples in the experiment */
        int experimentSamples,
        /** Number of successes observed */
        int experimentSuccesses,
        /** Observed success rate: successes / samples */
        double observedRate,
        /** Standard error of the experimental estimate */
        double standardError,
        /** Reference to the source baseline file */
        String baselineReference
    ) {}
    
    /**
     * Configuration for the regression test.
     */
    public record TestConfiguration(
        /** Number of samples for the regression test */
        int testSamples,
        /** Confidence level for threshold derivation (e.g., 0.95) */
        double confidenceLevel
    ) {}
    
    /**
     * Metadata about how the threshold was derived.
     */
    public record DerivationMetadata(
        /** The method used for derivation */
        DerivationMethod method,
        /** Z-score used in calculation */
        double zScore,
        /** Standard error for test sample size */
        double testStandardError,
        /** Timestamp of derivation */
        Instant derivedAt
    ) {}
    
    public enum DerivationMethod {
        /** 
         * Normal approximation one-sided lower bound: p̂ - z_α × SE
         * 
         * Use when: n ≥ 40 AND p̂ is not near 0 or 1 (i.e., 0.1 ≤ p̂ ≤ 0.9)
         * Avoid when: n < 20 OR p̂ < 0.1 OR p̂ > 0.9
         * Never use when: n < 10
         */
        NORMAL_APPROXIMATION,
        
        /** 
         * Wilson score one-sided lower bound.
         * 
         * Use when:
         *   - n < 20 (strongly recommended)
         *   - n < 40 and p̂ near 0 or 1 (preferred)
         *   - p̂ > 0.9 or p̂ < 0.1 (preferred, any sample size)
         *   - n < 10 (required; normal approximation should not be used)
         * 
         * The Wilson bound has better coverage properties than normal
         * approximation and never produces bounds outside [0, 1].
         */
        WILSON_SCORE,
        
        /** 
         * Clopper-Pearson exact one-sided lower bound (conservative).
         * 
         * Guarantees coverage but may be overly conservative.
         * Consider for very small samples (n < 10) when exact coverage is critical.
         */
        EXACT_BINOMIAL
    }
    
    // Note: All methods compute ONE-SIDED LOWER bounds only.
    // Upper bounds are not computed because regression testing
    // is only concerned with detecting degradation, not improvement.
}
```

### 3.4 Threshold Calculator

```java
/**
 * Calculates statistically-derived regression test thresholds.
 */
public class RegressionThresholdCalculator {
    
    private static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    
    /**
     * Calculates the minimum pass rate threshold for a regression test.
     *
     * @param experimentSamples number of samples in the experiment
     * @param experimentSuccesses number of successes observed
     * @param testSamples intended number of samples for regression test
     * @param confidenceLevel confidence level (default: 0.95)
     * @return the derived threshold with full traceability
     */
    public RegressionThreshold calculate(
            int experimentSamples,
            int experimentSuccesses,
            int testSamples,
            double confidenceLevel) {
        
        double pHat = (double) experimentSuccesses / experimentSamples;
        double zScore = getZScore(confidenceLevel);
        
        // Choose derivation method based on sample characteristics
        DerivationMethod method = chooseMethod(pHat, testSamples);
        
        double minPassRate;
        double testSE;
        
        if (method == DerivationMethod.WILSON_SCORE) {
            minPassRate = wilsonLowerBound(pHat, testSamples, zScore);
            testSE = Math.sqrt(pHat * (1 - pHat) / testSamples);
        } else {
            // Normal approximation
            testSE = Math.sqrt(pHat * (1 - pHat) / testSamples);
            minPassRate = pHat - zScore * testSE;
        }
        
        // Clamp to valid probability range
        minPassRate = Math.max(0.0, Math.min(1.0, minPassRate));
        
        return new RegressionThreshold(
            new ExperimentalBasis(
                experimentSamples,
                experimentSuccesses,
                pHat,
                Math.sqrt(pHat * (1 - pHat) / experimentSamples),
                null  // baseline reference added by caller
            ),
            new TestConfiguration(testSamples, confidenceLevel),
            minPassRate,
            new DerivationMetadata(
                method,
                zScore,
                testSE,
                Instant.now()
            )
        );
    }
    
    private DerivationMethod chooseMethod(double pHat, int n) {
        // Method selection based on statistical best practices:
        //
        // | Condition                       | Recommendation                     |
        // |---------------------------------|------------------------------------|
        // | n ≥ 40 and p̂ not near 0 or 1   | Normal approximation OK            |
        // | n ≥ 20 but p̂ near 0 or 1       | Wilson preferred                   |
        // | n < 20                          | Wilson strongly recommended        |
        // | n < 10                          | Normal approximation should NOT be used |
        
        boolean pNearExtreme = pHat < 0.1 || pHat > 0.9;
        
        if (n < 10) {
            // Normal approximation is unreliable; Wilson required
            return DerivationMethod.WILSON_SCORE;
        }
        if (n < 20) {
            // Wilson strongly recommended for small samples
            return DerivationMethod.WILSON_SCORE;
        }
        if (n < 40 && pNearExtreme) {
            // Wilson preferred when sample is moderate and rate is extreme
            return DerivationMethod.WILSON_SCORE;
        }
        if (pNearExtreme) {
            // Even with large n, Wilson is preferred for extreme rates
            // (provides better coverage properties)
            return DerivationMethod.WILSON_SCORE;
        }
        
        // n ≥ 40 and p̂ is not near 0 or 1: normal approximation is fine
        return DerivationMethod.NORMAL_APPROXIMATION;
    }
    
    private double wilsonLowerBound(double pHat, int n, double z) {
        double z2 = z * z;
        double numerator = pHat + z2 / (2 * n) 
                         - z * Math.sqrt(pHat * (1 - pHat) / n + z2 / (4 * n * n));
        double denominator = 1 + z2 / n;
        return numerator / denominator;
    }
    
    private double getZScore(double confidenceLevel) {
        // Common values; could use normal distribution for arbitrary levels
        if (confidenceLevel >= 0.99) return 2.326;
        if (confidenceLevel >= 0.95) return 1.645;
        if (confidenceLevel >= 0.90) return 1.282;
        if (confidenceLevel >= 0.80) return 0.842;
        return 1.645; // default to 95%
    }
}
```

---

## 4. Enhanced Specification Model

### 4.1 Updated ExecutionSpecification

The specification model should be extended to include regression threshold information:

```yaml
# specs/usecase.json.generation/v1.yaml
specId: usecase.json.generation:v1
useCaseId: usecase.json.generation
version: 1

approvedAt: 2026-01-04T17:00:00Z
approvedBy: jane.engineer@example.com
approvalNotes: |
  Derived from experiment with 1000 samples (95.1% success).
  Threshold adjusted for 100-sample regression tests at 95% confidence.

# Reference to source baseline
sourceBaselines:
  - baselines/usecase-json-generation-experiment.yaml

# Execution context
executionContext:
  backend: llm
  model: gpt-4
  temperature: 0.2

# NEW: Regression threshold configuration
regressionThreshold:
  # Experimental basis
  experimentalBasis:
    samples: 1000
    successes: 951
    observedRate: 0.951
    standardError: 0.0068
    
  # Test configuration
  testConfiguration:
    samples: 100
    confidenceLevel: 0.95
    
  # Derived threshold
  derivedMinPassRate: 0.916
  
  # Derivation details (for auditability)
  derivation:
    method: NORMAL_APPROXIMATION
    zScore: 1.645
    testStandardError: 0.0216
    derivedAt: 2026-01-04T17:00:00Z
    
  # Human-readable explanation
  explanation: |
    The minimum pass rate of 91.6% is derived from the experimental 
    observation of 95.1% (951/1000) adjusted for the smaller test 
    sample size (100). This threshold provides 95% confidence that 
    a passing test indicates no degradation from experimental levels.

# Legacy field (for backward compatibility)
requirements:
  # This is now derived from regressionThreshold.derivedMinPassRate
  minPassRate: 0.916
  successCriteria: "isValidJson == true"
```

### 4.2 Baseline Enhancement

The empirical baseline should also record data needed for threshold derivation:

```yaml
# baselines/usecase-json-generation-experiment.yaml
useCaseId: usecase.json.generation
experimentId: json-generation-baseline
generatedAt: 2026-01-04T15:00:00Z

execution:
  samplesPlanned: 1000
  samplesExecuted: 1000
  terminationReason: COMPLETED

statistics:
  successRate:
    observed: 0.9510
    standardError: 0.0068
    confidenceInterval95: [0.9376, 0.9644]
  successes: 951
  failures: 49

# NEW: Pre-computed one-sided lower bounds for common test sample sizes
# These thresholds detect degradation with 95% confidence
derivedThresholds:
  - testSamples: 50
    confidenceLevel: 0.95
    minPassRate: 0.901
    boundType: ONE_SIDED_LOWER
    explanation: "One-sided lower bound for 50-sample tests"
    
  - testSamples: 100
    confidenceLevel: 0.95
    minPassRate: 0.916
    boundType: ONE_SIDED_LOWER
    explanation: "One-sided lower bound for 100-sample tests"
    
  - testSamples: 200
    confidenceLevel: 0.95
    minPassRate: 0.927
    boundType: ONE_SIDED_LOWER
    explanation: "One-sided lower bound for 200-sample tests"
    
  - testSamples: 500
    confidenceLevel: 0.95
    minPassRate: 0.937
    boundType: ONE_SIDED_LOWER
    explanation: "One-sided lower bound for 500-sample tests"
```

---

## 5. API Integration

### 5.1 Enhanced @ProbabilisticTest Annotation

Add support for spec-driven threshold resolution:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ProbabilisticTestExtension.class)
public @interface ProbabilisticTest {
    
    // Existing parameters...
    int samples() default 100;
    double minPassRate() default 0.95;
    
    /**
     * Reference to an ExecutionSpecification that defines the threshold.
     * When provided, minPassRate is derived from the specification based
     * on the configured sample count.
     * 
     * Format: "specId:version" or just "specId" for latest version.
     * Example: "usecase.json.generation:v1"
     */
    String spec() default "";
    
    /**
     * Confidence level for threshold derivation when using spec-based thresholds.
     * Default: 0.95 (95% confidence).
     */
    double thresholdConfidence() default 0.95;
    
    /**
     * Behavior when spec references experimental data with different sample size.
     * Default: DERIVE (compute appropriate threshold for actual test samples).
     */
    ThresholdDerivationPolicy derivationPolicy() default ThresholdDerivationPolicy.DERIVE;
}

public enum ThresholdDerivationPolicy {
    /** 
     * Derive threshold from spec's experimental basis, adjusted for test sample size.
     * This is the recommended default.
     */
    DERIVE,
    
    /**
     * Use the raw minPassRate from spec without adjustment.
     * Warning: May cause false failures if test samples differ from experiment.
     */
    RAW,
    
    /**
     * Fail if test samples differ significantly from experiment.
     * Use when exact replication is required.
     */
    REQUIRE_MATCHING_SAMPLES
}
```

### 5.2 Usage Examples

**Spec-Driven Test with Automatic Threshold Derivation:**

```java
@ProbabilisticTest(
    spec = "usecase.json.generation:v1",
    samples = 100,  // Different from experiment's 1000
    thresholdConfidence = 0.95
)
void jsonGenerationMeetsSpec() {
    // minPassRate is automatically derived as ~0.916
    String result = llmClient.generateJson();
    assertThat(result).satisfies(JsonValidator::isValidJson);
}
```

**Explicit Threshold with Traceability:**

```java
@ProbabilisticTest(
    samples = 100,
    minPassRate = 0.916,  // Derived externally, documented in comments
    // Note: Based on 95.1% (951/1000) experiment, 95% CI lower bound
)
void jsonGenerationMeetsSpec() {
    String result = llmClient.generateJson();
    assertThat(result).satisfies(JsonValidator::isValidJson);
}
```

### 5.3 ConfigurationResolver Enhancement

The `ConfigurationResolver` should support threshold derivation:

```java
public class ConfigurationResolver {
    
    private final SpecificationRegistry specRegistry;
    private final RegressionThresholdCalculator thresholdCalculator;
    
    public ProbabilisticTestConfiguration resolve(
            ProbabilisticTest annotation,
            ExtensionContext context) {
        
        int samples = resolveSamples(annotation);
        double minPassRate;
        
        if (!annotation.spec().isEmpty()) {
            // Spec-driven: derive threshold from experimental basis
            minPassRate = deriveThresholdFromSpec(
                annotation.spec(),
                samples,
                annotation.thresholdConfidence(),
                annotation.derivationPolicy()
            );
        } else {
            // Direct specification
            minPassRate = resolveMinPassRate(annotation);
        }
        
        return new ProbabilisticTestConfiguration(
            samples,
            minPassRate,
            // ... other configuration
        );
    }
    
    private double deriveThresholdFromSpec(
            String specRef,
            int testSamples,
            double confidence,
            ThresholdDerivationPolicy policy) {
        
        ExecutionSpecification spec = specRegistry.load(specRef);
        
        if (spec.getRegressionThreshold() != null) {
            RegressionThreshold threshold = spec.getRegressionThreshold();
            
            if (policy == ThresholdDerivationPolicy.RAW) {
                return threshold.minPassRate();
            }
            
            if (policy == ThresholdDerivationPolicy.REQUIRE_MATCHING_SAMPLES) {
                if (testSamples != threshold.testConfig().testSamples()) {
                    throw new IllegalArgumentException(
                        "Test samples (" + testSamples + ") differ from spec samples (" +
                        threshold.testConfig().testSamples() + "). " +
                        "Use DERIVE policy or match sample counts.");
                }
                return threshold.minPassRate();
            }
            
            // DERIVE: Compute threshold for actual test sample size
            return thresholdCalculator.calculate(
                threshold.basis().experimentSamples(),
                threshold.basis().experimentSuccesses(),
                testSamples,
                confidence
            ).minPassRate();
        }
        
        // Fallback: use raw minPassRate from spec
        return spec.getMinPassRate();
    }
}
```

---

## 6. Baseline-to-Spec Workflow Enhancement

### 6.1 Enhanced EmpiricalBaselineGenerator

When generating baselines, pre-compute thresholds for common test configurations:

```java
public class EmpiricalBaselineGenerator {
    
    private static final int[] COMMON_TEST_SAMPLE_SIZES = {50, 100, 200, 500};
    private static final double DEFAULT_CONFIDENCE = 0.95;
    
    private final RegressionThresholdCalculator thresholdCalculator;
    
    public EmpiricalBaseline generateWithDerivedThresholds(
            String useCaseId,
            AggregatedResults results) {
        
        int samples = results.getSamplesExecuted();
        int successes = results.getSuccesses();
        
        // Compute thresholds for common test sample sizes
        List<DerivedThreshold> derivedThresholds = new ArrayList<>();
        
        for (int testSamples : COMMON_TEST_SAMPLE_SIZES) {
            RegressionThreshold threshold = thresholdCalculator.calculate(
                samples, successes, testSamples, DEFAULT_CONFIDENCE
            );
            
            derivedThresholds.add(new DerivedThreshold(
                testSamples,
                DEFAULT_CONFIDENCE,
                threshold.minPassRate(),
                generateExplanation(samples, successes, testSamples, threshold)
            ));
        }
        
        return EmpiricalBaseline.builder()
            .useCaseId(useCaseId)
            .execution(buildExecutionSummary(results))
            .statistics(buildStatisticsSummary(results))
            .derivedThresholds(derivedThresholds)  // NEW
            .build();
    }
    
    private String generateExplanation(
            int expSamples, int expSuccesses, int testSamples, 
            RegressionThreshold threshold) {
        
        double expRate = (double) expSuccesses / expSamples;
        return String.format(
            "Derived from %.1f%% (%d/%d) experiment. " +
            "Lower bound for %d-sample tests at %.0f%% confidence.",
            expRate * 100, expSuccesses, expSamples,
            testSamples, threshold.testConfig().confidenceLevel() * 100
        );
    }
}
```

### 6.2 Spec Generation Tool

Provide a command-line or programmatic tool for generating specifications from baselines:

```java
/**
 * Generates ExecutionSpecification from EmpiricalBaseline with 
 * statistically-derived regression thresholds.
 */
public class SpecificationGenerator {
    
    public ExecutionSpecification generateSpec(
            EmpiricalBaseline baseline,
            int targetTestSamples,
            double confidenceLevel,
            ApprovalMetadata approval) {
        
        int expSamples = baseline.getExecution().samplesExecuted();
        int expSuccesses = baseline.getStatistics().successes();
        
        RegressionThreshold threshold = thresholdCalculator.calculate(
            expSamples, expSuccesses, targetTestSamples, confidenceLevel
        );
        
        return ExecutionSpecification.builder()
            .specId(generateSpecId(baseline.getUseCaseId()))
            .useCaseId(baseline.getUseCaseId())
            .version(1)
            .approvedAt(approval.timestamp())
            .approvedBy(approval.approver())
            .approvalNotes(approval.notes())
            .sourceBaselines(List.of(baseline.getBaselineReference()))
            .executionContext(baseline.getContext())
            .regressionThreshold(threshold)
            .requirements(threshold.minPassRate(), baseline.getSuccessCriteriaDefinition())
            .build();
    }
}
```

---

## 7. Reporting and Diagnostics

### 7.1 Enhanced Test Reports

When tests use spec-derived thresholds, reports should include traceability:

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.jsonGenerationMeetsSpec                 │
├─────────────────────────────────────────────────────────────────┤
│ Status: PASSED                                                   │
│ Samples: 100/100 executed                                        │
│ Successes: 94                                                    │
│ Failures: 6                                                      │
│ Observed Pass Rate: 94.00%                                       │
│ Minimum Pass Rate: 91.55% (derived, one-sided lower bound)       │
│                                                                  │
│ THRESHOLD DERIVATION (ONE-SIDED LOWER BOUND):                    │
│   Experimental basis: 95.1% (951/1000 samples)                   │
│   Test sample size: 100                                          │
│   Confidence level: 95% (one-sided)                              │
│   Method: NORMAL_APPROXIMATION                                   │
│   Standard error (test): 2.16%                                   │
│   One-sided lower bound: 95.1% - 1.645 × 2.16% = 91.55%          │
│                                                                  │
│ INTERPRETATION:                                                  │
│   Observed 94.00% ≥ 91.55% threshold → No evidence of degradation│
│   (We only test for degradation; improvements are always OK)     │
│                                                                  │
│ Source: spec usecase.json.generation:v1                          │
│         baseline baselines/json-generation-exp.yaml              │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Failure Diagnostics

When a test fails, indicate whether the failure is significant given the statistical context:

```
┌─────────────────────────────────────────────────────────────────┐
│ TEST: JsonGenerationTest.jsonGenerationMeetsSpec                 │
├─────────────────────────────────────────────────────────────────┤
│ Status: FAILED                                                   │
│ Samples: 100/100 executed                                        │
│ Successes: 87                                                    │
│ Failures: 13                                                     │
│ Observed Pass Rate: 87.00%                                       │
│ Minimum Pass Rate: 91.55% (one-sided lower bound)                │
│                                                                  │
│ DEGRADATION ANALYSIS (ONE-TAILED TEST):                          │
│   Observed: 87.0% (87/100)                                       │
│   Experimental baseline: 95.1%                                   │
│   Shortfall: 4.55% below threshold                               │
│   Z-score: (87.0 - 95.1) / 2.16 = -3.75                          │
│   One-tailed p-value: < 0.001                                    │
│                                                                  │
│ INTERPRETATION:                                                  │
│   This result indicates statistically significant DEGRADATION.   │
│   The system performs worse than the experimental baseline.      │
│   This is unlikely (p < 0.1%) to be random sampling variation.   │
│                                                                  │
│ NOTE: This is a one-sided test. We only flag degradation.        │
│       Performance above baseline would always pass.              │
│                                                                  │
│ RECOMMENDATIONS:                                                 │
│   1. Investigate recent changes that may have caused regression  │
│   2. Re-run experiment to establish new baseline if intentional  │
│   3. If false positive suspected, increase test sample size      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. Validation and Safety

### 8.1 Threshold Validation

The framework should validate derived thresholds:

```java
public class ThresholdValidator {
    
    public void validate(RegressionThreshold threshold) {
        int nTest = threshold.testConfig().testSamples();
        int nExp = threshold.basis().experimentSamples();
        double pHat = threshold.basis().observedRate();
        DerivationMethod method = threshold.derivation().method();
        
        // === Sample size validations ===
        
        if (nTest < 10 && method == DerivationMethod.NORMAL_APPROXIMATION) {
            throw new IllegalStateException(
                "Normal approximation should NOT be used for n < 10. " +
                "Wilson score is required for test sample size " + nTest);
        }
        
        if (nTest < 20 && method == DerivationMethod.NORMAL_APPROXIMATION) {
            logWarning(
                "Normal approximation is unreliable for n < 20 (n_test = %d). " +
                "Wilson score is strongly recommended.",
                nTest);
        }
        
        if (nExp < 100) {
            logWarning(
                "Experimental sample size (%d) is small. " +
                "Derived thresholds may be unreliable due to imprecise baseline estimate.",
                nExp);
        }
        
        // === Success rate validations ===
        
        boolean pNearExtreme = pHat < 0.1 || pHat > 0.9;
        
        if (pNearExtreme && method == DerivationMethod.NORMAL_APPROXIMATION) {
            logWarning(
                "Success rate (%.1f%%) is near 0 or 1. " +
                "Wilson score is preferred for extreme rates.",
                pHat * 100);
        }
        
        if (pHat > 0.99 || pHat < 0.01) {
            logWarning(
                "Success rate (%.2f%%) is very extreme. " +
                "Wilson score bound will be more accurate.",
                pHat * 100);
        }
        
        // === Threshold sanity checks ===
        
        if (threshold.minPassRate() < 0.5) {
            logWarning(
                "Derived threshold %.2f%% is very low. " +
                "Consider increasing test samples or re-running experiment.",
                threshold.minPassRate() * 100);
        }
        
        // Sample size ratio check
        double ratio = (double) nTest / nExp;
        if (ratio > 0.5) {
            logInfo(
                "Test sample size (%.0f%% of experiment) is large enough " +
                "that threshold adjustment is minimal.",
                ratio * 100);
        }
    }
}
```

### 8.2 Sample Size Requirements and Method Selection

#### Test Sample Size Guidelines

| Test Sample Size | Method Selection | Notes |
|------------------|------------------|-------|
| n_test ≥ 40 | Normal OK if p̂ not extreme | Standard recommendation |
| 20 ≤ n_test < 40 | Wilson preferred | Especially if p̂ > 0.9 or < 0.1 |
| 10 ≤ n_test < 20 | **Wilson strongly recommended** | Normal approximation unreliable |
| n_test < 10 | **Wilson required** | Normal approximation should NOT be used |

#### Experimental Sample Size Guidelines

| Experimental Samples | Guidance | Rationale |
|---------------------|----------|-----------|
| n_exp ≥ 1000 | Excellent | Very precise baseline estimate |
| 500 ≤ n_exp < 1000 | Good | Adequate precision for most uses |
| 100 ≤ n_exp < 500 | Acceptable | Consider larger confidence margins |
| n_exp < 100 | **Caution** | Experimental uncertainty may be too high; derived thresholds less reliable |

#### Combined Guidance

The reliability of derived thresholds depends on both experimental and test sample sizes:

```
If n_exp < 100:
    WARN: "Experimental sample size is small; derived thresholds may be unreliable"
    
If n_test < 10:
    REQUIRE: Wilson score method (never use normal approximation)
    WARN: "Test sample size is very small; consider increasing samples"
    
If n_test < 20:
    PREFER: Wilson score method
    
If p̂_exp > 0.9 OR p̂_exp < 0.1:
    PREFER: Wilson score method (regardless of sample size)
```

#### 8.2.1 Economic Trade-offs: Cost vs Statistical Power

Since regression tests run frequently (see Section 1.4), teams face a fundamental trade-off:

| Sample Size | Cost | Statistical Power | Threshold Margin | Recommendation |
|-------------|------|-------------------|------------------|----------------|
| n = 20 | Very low | Low | ~15% margin | Acceptable for early dev; risky for production gates |
| n = 50 | Low | Moderate | ~10% margin | **Reasonable minimum for CI** |
| n = 100 | Moderate | Good | ~7% margin | **Good balance for most use cases** |
| n = 200 | Higher | Very good | ~5% margin | Recommended for critical paths |
| n = 500 | High | Excellent | ~3% margin | Use when false negatives are costly |

**"Threshold margin"** = the gap between experimental rate and derived test threshold (e.g., 95.1% → 91.6% is a 3.5% margin).

**Guidance for teams**:

1. **Experiments can be expensive**: Run large experiments (n=500–1000) infrequently to establish high-quality baselines. These costs are amortized over many regression test runs.

2. **Regression tests should be economical**: Run smaller tests (n=50–100) frequently. The statistical machinery (Wilson bounds, one-sided tests) ensures these remain valid despite higher variance.

3. **Critical paths deserve more samples**: For use cases where false negatives (missed regressions) are costly, invest in larger test samples (n=200+).

4. **Very small samples (n < 20) have limited value**: The margin becomes so large that only severe regressions are detectable. Consider whether the test provides meaningful signal.

**Example cost analysis**:

```
Use case: JSON generation with GPT-4
- Experimental baseline: 1000 samples @ $0.03/sample = $30 (one-time)
- Daily regression test options:
  - n=50:  $1.50/run × 30 days = $45/month (margin ~10%)
  - n=100: $3.00/run × 30 days = $90/month (margin ~7%)
  - n=200: $6.00/run × 30 days = $180/month (margin ~5%)
```

The choice depends on how much regression risk the team is willing to accept for the cost savings.

---

## 9. Implementation Plan

### Phase 1: Core Calculation Engine (2-3 days)

**Deliverables:**
1. `RegressionThreshold` record with all fields
2. `RegressionThresholdCalculator` with normal approximation and Wilson score
3. Unit tests for calculator with known inputs/outputs
4. Validation logic for edge cases

**Acceptance Criteria:**
- Calculator produces correct thresholds for worked examples
- Wilson score used appropriately for small samples/extreme rates
- Thresholds clamped to valid probability range [0, 1]

### Phase 2: Model Integration (2-3 days)

**Deliverables:**
1. Enhanced `EmpiricalBaseline` with derived thresholds
2. Enhanced `ExecutionSpecification` with regression threshold section
3. Updated `BaselineWriter` to emit derived thresholds
4. YAML/JSON parsing for new fields

**Acceptance Criteria:**
- Baselines include pre-computed thresholds for common test sizes
- Specifications support regression threshold configuration
- Backward compatibility with existing baseline/spec files

### Phase 3: Annotation and Resolution (2-3 days)

**Deliverables:**
1. `@ProbabilisticTest.spec` attribute
2. `@ProbabilisticTest.thresholdConfidence` attribute
3. `ThresholdDerivationPolicy` enum
4. Enhanced `ConfigurationResolver` with spec-based threshold derivation

**Acceptance Criteria:**
- Tests can reference specs by ID
- Thresholds are derived automatically based on test sample size
- Policy options work correctly (DERIVE, RAW, REQUIRE_MATCHING_SAMPLES)

### Phase 4: Reporting Enhancement (1-2 days)

**Deliverables:**
1. Enhanced test reports with threshold derivation details
2. Statistical analysis in failure messages
3. `ThresholdValidator` with warnings for edge cases

**Acceptance Criteria:**
- Reports show derivation traceability
- Failures include statistical context
- Warnings emitted for suspicious configurations

### Phase 5: Documentation and Examples (1-2 days)

**Deliverables:**
1. Javadoc for all new public API
2. Examples in test sources
3. Updates to README and QUICK-INTRO
4. Migration guide for existing specs

**Estimated Total Effort:** 8-13 days

---

## 10. Mathematical Appendix

### A.1 Why One-Sided Bounds Are Appropriate

In regression testing, we are performing a **one-tailed hypothesis test**:

- **Null hypothesis (H₀)**: The system performs at least as well as the experimental baseline (p ≥ p_exp)
- **Alternative hypothesis (H₁)**: The system has degraded (p < p_exp)

We only reject H₀ (fail the test) when we have sufficient evidence that the pass rate has **fallen**. We never reject because the pass rate is **higher** than expected—that's a good outcome.

This asymmetry is fundamental to regression testing:
- **False positive (Type I error)**: Failing a test when the system hasn't degraded → Wastes developer time investigating non-issues
- **False negative (Type II error)**: Passing a test when the system has degraded → Allows bugs into production

One-sided bounds optimize the balance: for a given confidence level (1-α), they provide maximum power to detect degradation while controlling the false positive rate.

### A.2 Normal Approximation Derivation

For a binomial proportion, the sampling distribution is approximately normal for large n:

```
p̂ ~ N(p, p(1-p)/n)
```

The standard error is:
```
SE = √(p(1-p)/n)
```

Since we don't know the true p, we estimate it with p̂:
```
SE ≈ √(p̂(1-p̂)/n)
```

For a **one-sided** (1-α) confidence lower bound:
```
p_lower = p̂ - z_α × SE
```

**Key distinction from two-sided intervals**: 
- One-sided uses z_α (e.g., 1.645 for 95%)
- Two-sided uses z_{α/2} (e.g., 1.96 for 95%)

The one-sided bound is less conservative, providing a higher threshold that is more sensitive to detecting degradation.

### A.3 Wilson Score Lower Bound

The Wilson score lower bound is derived from inverting the score test for a binomial proportion:

```
p_lower = (p̂ + z²/2n - z√(p̂(1-p̂)/n + z²/4n²)) / (1 + z²/n)
```

This formula gives **only the lower bound**. The upper bound formula exists but is not needed for regression testing.

#### When to Use Wilson vs Normal Approximation

| Condition | Recommendation | Notes |
|-----------|----------------|-------|
| n ≥ 40 **and** p̂ not near 0 or 1 | Normal approximation OK | CLT provides good coverage |
| n ≥ 20 **but** p̂ near 0 or 1 | **Wilson preferred** | Better coverage at extremes |
| n < 20 | **Wilson strongly recommended** | Normal increasingly unreliable |
| n < 10 | **Wilson required** | Normal approximation fails |

**"Near 0 or 1"** typically means p̂ < 0.1 or p̂ > 0.9.

#### Why Wilson is More Robust

The Wilson bound has better coverage properties because:

1. **Never produces invalid bounds**: Unlike normal approximation, Wilson can never produce p_lower < 0 or > 1.

2. **Better coverage at extremes**: When p̂ is near 0 or 1, the normal approximation's symmetric confidence interval is inappropriate for a bounded parameter.

3. **Correct asymptotic behavior**: The Wilson interval is derived from the score test, which has better small-sample properties than the Wald test underlying the normal approximation.

4. **Minimal cost**: Wilson is only slightly more complex to compute and has no drawbacks for large samples.

### A.4 Reference Table: One-Sided Lower Bound Examples

The following table shows **one-sided 95% lower bounds** for various experimental and test configurations. These thresholds ensure that if the true system performance matches the experiment, only 5% of tests will falsely fail due to sampling variance.

| Exp. Rate | Exp. N | Test N | 95% One-Sided Lower Bound | Margin | Method Used |
|-----------|--------|--------|---------------------------|--------|-------------|
| 95% | 1000 | 100 | 91.4% | ~3.6% | Wilson (p̂ > 0.9) |
| 95% | 1000 | 50 | 89.9% | ~5.1% | Wilson (p̂ > 0.9) |
| 95% | 1000 | 20 | 86.9% | ~8.1% | Wilson (n < 40, p̂ > 0.9) |
| 95% | 500 | 100 | 90.6% | ~4.4% | Wilson (p̂ > 0.9) |
| 90% | 1000 | 100 | 85.1% | ~4.9% | Normal OK |
| 99% | 1000 | 100 | 97.4% | ~1.6% | Wilson (p̂ > 0.9) |
| 80% | 1000 | 100 | 73.4% | ~6.6% | Normal OK |
| 85% | 1000 | 50 | 76.7% | ~8.3% | Normal OK |

**Key observations for cost-conscious teams**:

1. **Smaller test samples require larger margins**: Reducing n from 100 to 50 roughly doubles the margin (3.6% → 5.1% for 95% rate).

2. **High success rates are less forgiving**: At p̂ = 99%, even with n=100, the margin is only 1.6%. A few bad samples can cause failure.

3. **Diminishing returns on large samples**: Going from n=100 to n=500 improves margin from ~3.6% to ~1.6%, but costs 5× more.

4. **The "sweet spot" for most teams**: n=50–100 provides reasonable margins (5–7%) at manageable cost.

**Interpretation**: The "margin" is the amount subtracted from the experimental rate to account for the higher variance in smaller test samples. Larger margins mean the test is less sensitive to small regressions—a trade-off teams make for cost reasons.

---

## 11. Open Questions

| Question | Recommendation |
|----------|----------------|
| **Default confidence level?** | **95%**. Industry standard for statistical testing. Configurable for users who need different levels. |
| **Which interval method by default?** | **Wilson score**. Given economic pressure for small test samples (n=50–100) and typically high success rates (p̂ > 0.85), Wilson is the safer default. Normal approximation can be used when conditions clearly favor it (n ≥ 40 and 0.1 ≤ p̂ ≤ 0.9), but Wilson has no downside in these cases either. |
| **Should thresholds be cached?** | **Yes**, in the specification. Re-computation is cheap but caching ensures consistency and auditability. |
| **How to handle missing experimental data?** | **Fail loudly**. If a spec references experimental basis that isn't available, the test should fail at discovery time with a clear error. |
| **Why not two-sided bounds?** | **By design, we use one-sided (lower) bounds only.** Regression testing is concerned exclusively with detecting degradation—when pass rates fall below acceptable levels. Detecting unexpected improvements serves no purpose in CI gating: a test that performs better than expected should still pass. One-sided bounds also provide more statistical power for detecting degradation compared to two-sided bounds at the same confidence level. |

---

## 12. Glossary

| Term | Definition |
|------|------------|
| **Bernoulli trial** | A single random experiment with two outcomes: success (1) or failure (0) |
| **Binomial distribution** | Distribution of the number of successes in n independent Bernoulli trials |
| **Point estimate** | A single value estimate of a population parameter (e.g., p̂ = k/n) |
| **Standard error** | The standard deviation of the sampling distribution of an estimator |
| **One-sided confidence bound** | A threshold value below (or above) which the true parameter lies with specified probability. For regression testing, we use the **lower** bound. |
| **Two-sided confidence interval** | A range with both lower and upper bounds; **not used** in this context because we only care about detecting degradation |
| **Lower confidence bound** | The threshold below which the true success rate is unlikely to fall. Used as the minimum pass rate for regression tests. |
| **Normal approximation** | Using the normal distribution to approximate binomial probabilities; suitable for large samples |
| **Wilson score bound** | A more robust one-sided bound for binomial proportions; suitable for small samples or extreme rates |
| **Z-score (z_α)** | Number of standard deviations from the mean for a one-sided confidence level. E.g., z₀.₀₅ = 1.645 for 95% one-sided confidence |
| **Degradation** | A reduction in the true success probability compared to the experimental baseline; the primary concern of regression testing |

---

*End of ESP-P Plan*

