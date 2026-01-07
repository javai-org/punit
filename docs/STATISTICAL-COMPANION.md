# Statistical Companion Document

## Formal Statistical Foundations for PUnit

This document provides a rigorous statistical treatment of the methods employed by PUnit for probabilistic testing of non-deterministic systems. It is intended for professional statisticians, quality engineers with statistical training, and auditors who need to validate the mathematical foundations of the framework.

For operational context and workflow guidance, see the companion document: [Operational Flow](./OPERATIONAL-FLOW.md).

---

## Running Example

Throughout this document, we use a single example to illustrate the statistical methods:

**Application**: A customer service system that accepts natural language queries and uses a Large Language Model (LLM) to generate structured JSON responses containing customer information lookups.

**Use Case**: Given a natural language query (e.g., "What's the shipping status for order #12345?"), the system should return valid JSON with the required fields (`orderId`, `status`, `estimatedDelivery`).

**Success Criterion**: A trial is successful if:
1. The response is syntactically valid JSON
2. The response contains all required fields
3. The field values are semantically appropriate (not hallucinated)

**Characteristic**: Due to the stochastic nature of the LLM, identical inputs may produce different outputs across invocations. The system exhibits non-deterministic behavior with an unknown but stable success probability *p*.

---

## 1. Statistical Model

### 1.1 Bernoulli Trial Framework

Each invocation of the use case is modeled as an independent Bernoulli trial:

$$X_i \sim \text{Bernoulli}(p)$$

where:
- $X_i \in \{0, 1\}$ is the outcome of the *i*-th trial (1 = success, 0 = failure)
- $p \in [0, 1]$ is the true (unknown) success probability
- Trials are assumed independent and identically distributed (i.i.d.)

### 1.2 Binomial Aggregation

For *n* independent trials, the total number of successes follows a binomial distribution:

$$K = \sum_{i=1}^{n} X_i \sim \text{Binomial}(n, p)$$

The sample proportion $\hat{p} = K/n$ is an unbiased estimator of *p*:

$$E[\hat{p}] = p, \quad \text{Var}(\hat{p}) = \frac{p(1-p)}{n}$$

### 1.3 Assumptions and Limitations

The Bernoulli model assumes:

1. **Independence**: Each trial is independent. In practice, this may be violated if:
   - The LLM provider implements request-level caching
   - Rate limiting causes correlated delays
   - Model state persists across requests (generally not the case for stateless APIs)

2. **Stationarity**: The success probability *p* is constant across trials. This may be violated if:
   - The LLM provider updates the model during the experiment
   - System load affects response quality
   - Input distribution changes during execution

3. **Binary outcomes**: Each trial has exactly two outcomes. Complex quality metrics may require more sophisticated models.

**Recommendation**: For most LLM-based systems accessed via stateless APIs, these assumptions are reasonable. Monitor for temporal drift in long-running experiments.

---

## 2. Baseline Estimation (Experiment Phase)

### 2.1 Point Estimation

Given *n* experimental trials with *k* successes, the maximum likelihood estimator (MLE) for *p* is:

$$\hat{p} = \frac{k}{n}$$

**Example**: In our JSON generation use case, an experiment with n = 1000 trials yields k = 951 successes.

$$\hat{p} = \frac{951}{1000} = 0.951$$

### 2.2 Standard Error

The standard error of $\hat{p}$ quantifies the precision of the estimate:

$$\text{SE}(\hat{p}) = \sqrt{\frac{\hat{p}(1-\hat{p})}{n}}$$

**Example**:
$$\text{SE} = \sqrt{\frac{0.951 \times 0.049}{1000}} = \sqrt{0.0000466} \approx 0.00683$$

### 2.3 Confidence Intervals

#### 2.3.1 Wald Interval (Normal Approximation)

For large *n*, by the Central Limit Theorem:

$$\hat{p} \stackrel{a}{\sim} N\left(p, \frac{p(1-p)}{n}\right)$$

The $(1-\alpha)$ Wald confidence interval is:

$$\hat{p} \pm z_{\alpha/2} \cdot \text{SE}(\hat{p})$$

where $z_{\alpha/2}$ is the $(1-\alpha/2)$ quantile of the standard normal distribution.

**Example** (95% CI, $z_{0.025} = 1.96$):
$$0.951 \pm 1.96 \times 0.00683 = [0.938, 0.964]$$

#### 2.3.2 Wilson Score Interval

The Wilson score interval has better coverage properties, especially for:
- Small sample sizes (*n* < 40)
- Proportions near 0 or 1 (*p* < 0.1 or *p* > 0.9)

The Wilson interval endpoints are:

$$\frac{\hat{p} + \frac{z^2}{2n} \pm z\sqrt{\frac{\hat{p}(1-\hat{p})}{n} + \frac{z^2}{4n^2}}}{1 + \frac{z^2}{n}}$$

**Example** (95% CI):
$$\text{Lower} = \frac{0.951 + \frac{1.96^2}{2000} - 1.96\sqrt{\frac{0.951 \times 0.049}{1000} + \frac{1.96^2}{4000000}}}{1 + \frac{1.96^2}{1000}} \approx 0.937$$

$$\text{Upper} \approx 0.963$$

#### 2.3.3 Method Selection

| Condition                                            | Recommended Method                  |
|------------------------------------------------------|-------------------------------------|
| $n \geq 40$ and $0.1 \leq \hat{p} \leq 0.9$          | Wald acceptable                     |
| $n \geq 20$ and ($\hat{p} < 0.1$ or $\hat{p} > 0.9$) | Wilson preferred                    |
| $n < 20$                                             | Wilson strongly recommended         |
| $n < 10$                                             | Wilson required; Wald inappropriate |

**For PUnit**: Given typical use cases (high success rates *p* > 0.85, test sample sizes 50-200), the Wilson interval is the default.

### 2.4 Sample Size Determination

To achieve a desired margin of error *e* with confidence $(1-\alpha)$, the required sample size is approximately:

$$n = \frac{z_{\alpha/2}^2 \cdot \hat{p}(1-\hat{p})}{e^2}$$

**Example**: To estimate *p* ≈ 0.95 with ±2% margin at 95% confidence:

$$n = \frac{1.96^2 \times 0.95 \times 0.05}{0.02^2} = \frac{0.1825}{0.0004} \approx 456$$

| Target Precision (95% CI) | Required *n* (for *p* ≈ 0.95) |
|---------------------------|-------------------------------|
| ±5%                       | 73                            |
| ±3%                       | 203                           |
| ±2%                       | 456                           |
| ±1%                       | 1,825                         |

---

## 3. Threshold Derivation for Regression Testing

### 3.1 The Problem

The experiment established $\hat{p}_{\text{exp}} = 0.951$ from $n_{\text{exp}} = 1000$ samples. For cost reasons, regression tests will use $n_{\text{test}} = 100$ samples.

**Question**: What threshold $p_{\text{threshold}}$ should the regression test use?

**Naive approach**: Use $p_{\text{threshold}} = 0.951$.

**Problem with naive approach**: With only 100 samples, the standard error is:

$$\text{SE}_{\text{test}} = \sqrt{\frac{0.951 \times 0.049}{100}} \approx 0.0216$$

Even if the true *p* equals the experimental rate, observed rates will vary. At $\pm 2\sigma$, we'd expect observations between 0.908 and 0.994. Using 0.951 as the threshold would cause frequent false positives.

### 3.2 One-Sided Hypothesis Testing Framework

Regression testing is fundamentally a **one-sided hypothesis test**:

$$H_0: p \geq p_{\text{exp}} \quad \text{(no degradation)}$$
$$H_1: p < p_{\text{exp}} \quad \text{(degradation has occurred)}$$

We seek a decision rule that:
- Controls the Type I error rate (false positive) at level $\alpha$
- Maximizes power to detect true degradation

### 3.3 One-Sided Lower Confidence Bound

The $(1-\alpha)$ one-sided lower confidence bound is:

$$p_{\text{lower}} = \hat{p} - z_\alpha \cdot \text{SE}$$

Note: For one-sided bounds, we use $z_\alpha$ (not $z_{\alpha/2}$).

| Confidence Level | $z_\alpha$ | Interpretation          |
|------------------|------------|-------------------------|
| 90%              | 1.282      | 10% false positive rate |
| 95%              | 1.645      | 5% false positive rate  |
| 99%              | 2.326      | 1% false positive rate  |

### 3.4 Threshold Calculation (Normal Approximation)

Given experimental results $(\hat{p}_{\text{exp}}$, $n_{\text{exp}})$ and test configuration $(n_{\text{test}}$, $\alpha)$:

$$\text{SE}_{\text{test}} = \sqrt{\frac{\hat{p}_{\text{exp}}(1-\hat{p}_{\text{exp}})}{n_{\text{test}}}}$$

$$p_{\text{threshold}} = \hat{p}_{\text{exp}} - z_\alpha \cdot \text{SE}_{\text{test}}$$

**Example**:
- $\hat{p}_{\text{exp}} = 0.951$, $n_{\text{test}} = 100$, $\alpha = 0.05$
- $\text{SE}_{\text{test}} = \sqrt{0.951 \times 0.049 / 100} = 0.0216$
- $p_{\text{threshold}} = 0.951 - 1.645 \times 0.0216 = 0.951 - 0.0355 = 0.916$

**Interpretation**: A 100-sample test with threshold 0.916 will have a 5% false positive rate if the true success probability equals the experimental rate.

### 3.5 Wilson Score Lower Bound

For small $n_{\text{test}}$ or $\hat{p}$ near 0 or 1, use the Wilson one-sided lower bound:

$$p_{\text{lower}} = \frac{\hat{p} + \frac{z^2}{2n} - z\sqrt{\frac{\hat{p}(1-\hat{p})}{n} + \frac{z^2}{4n^2}}}{1 + \frac{z^2}{n}}$$

**Example** ($\hat{p} = 0.951$, $n = 100$, $z = 1.645$):

$$p_{\text{lower}} = \frac{0.951 + \frac{2.706}{200} - 1.645\sqrt{\frac{0.0466}{100} + \frac{2.706}{40000}}}{1 + \frac{2.706}{100}}$$

$$= \frac{0.951 + 0.0135 - 1.645 \times 0.0218}{1.027} = \frac{0.9286}{1.027} \approx 0.904$$

The Wilson bound (0.904) is slightly more conservative than the normal approximation (0.916) because $\hat{p} = 0.951$ is near the boundary.

### 3.6 Reference Table: Derived Thresholds

For experimental rate $\hat{p}_{\text{exp}} = 0.951$ from $n_{\text{exp}} = 1000$:

| Test Samples | Method | 95% Threshold | 99% Threshold |
|--------------|--------|---------------|---------------|
| 50           | Wilson | 0.890         | 0.869         |
| 100          | Wilson | 0.904         | 0.889         |
| 200          | Wilson | 0.918         | 0.907         |
| 500          | Wilson | 0.932         | 0.925         |

**Observation**: Smaller test samples require lower thresholds to maintain the same false positive rate.

---

## 4. The Perfect Baseline Problem ($\hat{p} = 1$)

### 4.1 Problem Statement

A critical pathology arises when the baseline experiment observes **zero failures**:

$$k = n \implies \hat{p} = 1$$

This commonly occurs when testing highly reliable systems (e.g., well-established third-party APIs) where failures are rare but not impossible.

**Example**: An experiment with $n = 1000$ trials against a payment gateway API yields $k = 1000$ successes.

### 4.2 Why Standard Methods Fail

#### 4.2.1 Standard Error Collapse

When $\hat{p} = 1$:

$$\text{SE}(\hat{p}) = \sqrt{\frac{1 \times 0}{n}} = 0$$

The plug-in standard error collapses to zero, regardless of sample size.

#### 4.2.2 Threshold Degeneracy

Using the normal approximation threshold formula:

$$p_{\text{threshold}} = \hat{p} - z_\alpha \cdot \text{SE} = 1 - z_\alpha \times 0 = 1$$

**Consequence**: Any single failure causes the test to fail, regardless of test sample size or confidence level. This produces an undefined (or effectively 50%) false positive rate.

#### 4.2.3 Wald Interval Collapse

The Wald confidence interval becomes $[1, 1]$—a degenerate point interval that provides no information about uncertainty.

### 4.3 Interpretation of 100% Observed Success

An observed rate of $\hat{p} = 1$ from $n$ trials does **not** mean $p = 1$. Rather, it provides evidence that:

$$P(p \geq p_{\text{lower}} \mid k=n, n) = 1 - \alpha$$

where $p_{\text{lower}}$ is derived using methods that remain valid at the boundary.

**The Rule of Three** (quick approximation): With $n$ trials and zero failures, we can be approximately 95% confident that:

$$p \geq 1 - \frac{3}{n}$$

| Baseline Samples | 95% Lower Bound (Rule of Three) |
|------------------|---------------------------------|
| 100              | 0.970                           |
| 300              | 0.990                           |
| 1000             | 0.997                           |
| 3000             | 0.999                           |

(Side note: this assumes conditions are stable and runs are independent.)

### 4.4 PUnit's Solution: Wilson Lower Bound

PUnit resolves this pathology using the **Wilson score lower bound**, which remains well-defined when $\hat{p} = 1$.

**Key implementation requirement**: Baselines store $(k, n)$, not merely $\hat{p}$. The observed rate can be computed ($\hat{p} = k/n$), but raw counts are essential for proper statistical treatment of boundary cases.

**Procedure**:
1. Compute the one-sided Wilson lower bound $p_0$ from the baseline $(k, n)$
2. Use $p_0$ (not $\hat{p}$) as the effective baseline for threshold derivation
3. Apply the standard threshold formula using $p_0$

#### 4.4.1 Wilson Lower Bound Formula

The general Wilson one-sided lower bound is:

$$p_{\text{lower}} = \frac{\hat{p} + \frac{z^2}{2n} - z\sqrt{\frac{\hat{p}(1-\hat{p})}{n} + \frac{z^2}{4n^2}}}{1 + \frac{z^2}{n}}$$

When $\hat{p} = 1$, this simplifies to:

$$p_{\text{lower}} = \frac{n}{n + z^2}$$

#### 4.4.2 Worked Example

**Baseline**: $n = 1000$ trials, $k = 1000$ successes (100% observed)

**Step 1**: Compute Wilson lower bound (95% one-sided, $z = 1.645$):

$$p_0 = \frac{1000}{1000 + 2.706} = \frac{1000}{1002.706} \approx 0.9973$$

**Step 2**: Derive test threshold for $n_{\text{test}} = 100$:

$$\text{SE}_{\text{test}} = \sqrt{\frac{0.9973 \times 0.0027}{100}} \approx 0.0052$$

$$p_{\text{threshold}} = 0.9973 - 1.645 \times 0.0052 = 0.9973 - 0.0086 \approx 0.989$$

**Interpretation**: For 100-sample tests, the threshold is 0.989 (at most 1 failure permitted). Compare this to the naive approach, which would set threshold = 1.0 (zero failures permitted), producing an undefined false positive rate.

#### 4.4.3 Reference Table: Thresholds for 100% Baselines

| Baseline $n$ | $p_0$ (Wilson 95%) | Test $n=50$ threshold | Test $n=100$ threshold |
|--------------|--------------------|-----------------------|------------------------|
| 100          | 0.9647             | 0.922                 | 0.935                  |
| 300          | 0.9883             | 0.964                 | 0.973                  |
| 1000         | 0.9973             | 0.985                 | 0.989                  |
| 3000         | 0.9991             | 0.993                 | 0.996                  |

### 4.5 Extended Example: Highly Reliable API

**Scenario**: Testing a payment gateway integration.

**Baseline experiment**: 2000 transactions, 0 failures ($\hat{p} = 1$).

**Goal**: Configure regression tests with 95% confidence.

**PUnit's calculation**:

1. Wilson lower bound: $p_0 = \frac{2000}{2000 + 2.706} = 0.9986$

2. For 100-sample test:
   - $\text{SE} = \sqrt{0.9986 \times 0.0014 / 100} = 0.0037$
   - $p_{\text{threshold}} = 0.9986 - 1.645 \times 0.0037 = 0.993$

3. For 50-sample test:
   - $\text{SE} = \sqrt{0.9986 \times 0.0014 / 50} = 0.0053$
   - $p_{\text{threshold}} = 0.9986 - 1.645 \times 0.0053 = 0.990$

**Result**: Even for this highly reliable system, PUnit produces statistically principled thresholds with valid confidence level interpretation.

### 4.6 Theoretical Note: Beta-Binomial Alternative

For statisticians reviewing this framework: the **Beta-Binomial posterior predictive** approach offers a theoretically superior treatment that fully propagates baseline uncertainty and produces integer thresholds. However, PUnit uses the Wilson bound because:

- It requires no prior specification
- It is simpler to implement and audit
- It produces results that are practically equivalent for typical sample sizes
- It remains within the frequentist paradigm familiar to most practitioners

Organizations with strong Bayesian infrastructure or specific requirements for integer thresholds may wish to implement their own threshold derivation using the posterior predictive:

$$K_t \mid k, n \sim \text{BetaBinomial}(n_t, a + k, b + n - k)$$

where $(a, b)$ are prior hyperparameters (Jeffreys: $a = b = 0.5$).

See Gelman et al. (2013) for a complete treatment.

---

## 5. Test Execution and Interpretation

### 5.1 Decision Rule

Given a test with $n_{\text{test}}$ samples and threshold $p_{\text{threshold}}$:

1. Execute use case $n_{\text{test}}$ times
2. Count successes $k_{\text{test}}$
3. Compute observed rate $\hat{p}_{\text{test}} = k_{\text{test}} / n_{\text{test}}$
4. Decision:
   - If $\hat{p}_{\text{test}} \geq p_{\text{threshold}}$: **PASS** (no evidence of degradation)
   - If $\hat{p}_{\text{test}} < p_{\text{threshold}}$: **FAIL** (statistically significant degradation)

### 5.2 Type I and Type II Errors

|                 | True state: No degradation    | True state: Degradation        |
|-----------------|-------------------------------|--------------------------------|
| **Test passes** | Correct (True Negative)       | Type II Error (False Negative) |
| **Test fails**  | Type I Error (False Positive) | Correct (True Positive)        |

- **Type I error rate** ($\alpha$): Controlled by threshold derivation. If threshold is set at $(1-\alpha)$ confidence, then $P(\text{False Positive}) = \alpha$.

- **Type II error rate** ($\beta$): Depends on:
  - True effect size (how much degradation occurred)
  - Sample size
  - Threshold

### 5.3 Statistical Power

Power is the probability of correctly detecting degradation when it exists:

$$\text{Power} = 1 - \beta = P(\text{Reject } H_0 | H_1 \text{ true})$$

For a one-sided test detecting a shift from $p_0$ to $p_1$ (where $p_1 < p_0$):

$$\text{Power} = \Phi\left(\frac{p_0 - p_1 - z_\alpha \cdot \text{SE}_0}{\text{SE}_1}\right)$$

where:
- $\text{SE}_0 = \sqrt{p_0(1-p_0)/n}$
- $\text{SE}_1 = \sqrt{p_1(1-p_1)/n}$
- $\Phi$ is the standard normal CDF

**Example**: Detecting a drop from $p_0 = 0.95$ to $p_1 = 0.90$ with $n = 100$ at $\alpha = 0.05$:

$$\text{SE}_0 = \sqrt{0.95 \times 0.05 / 100} = 0.0218$$
$$\text{SE}_1 = \sqrt{0.90 \times 0.10 / 100} = 0.0300$$
$$\text{Power} = \Phi\left(\frac{0.95 - 0.90 - 1.645 \times 0.0218}{0.0300}\right) = \Phi\left(\frac{0.0141}{0.0300}\right) = \Phi(0.47) \approx 0.68$$

With 100 samples, we have only 68% power to detect a 5-percentage-point degradation.

### 5.4 Sample Size for Desired Power

To achieve power $(1-\beta)$ for detecting effect size $\delta = p_0 - p_1$:

$$n = \left(\frac{z_\alpha \sqrt{p_0(1-p_0)} + z_\beta \sqrt{p_1(1-p_1)}}{\delta}\right)^2$$

**Example**: 80% power to detect 5% drop from 95% to 90% at $\alpha = 0.05$:

$$n = \left(\frac{1.645 \times 0.218 + 0.842 \times 0.300}{0.05}\right)^2 = \left(\frac{0.359 + 0.253}{0.05}\right)^2 = (12.24)^2 \approx 150$$

| Effect Size   | Power 80% | Power 90% | Power 95% |
|---------------|-----------|-----------|-----------|
| 5% (95%→90%)  | 150       | 200       | 250       |
| 10% (95%→85%) | 40        | 55        | 70        |
| 3% (95%→92%)  | 410       | 550       | 700       |

---

## 6. The Three Operational Approaches: Mathematical Formulation

### 6.1 Approach 1: Sample-Size-First

**Given**: $n_{\text{test}}$, $\alpha$, experimental basis $(\hat{p}_{\text{exp}}, n_{\text{exp}})$

**Compute**: $p_{\text{threshold}}$

$$p_{\text{threshold}} = \hat{p}_{\text{exp}} - z_\alpha \sqrt{\frac{\hat{p}_{\text{exp}}(1-\hat{p}_{\text{exp}})}{n_{\text{test}}}}$$

**Trade-off**: Fixed cost; confidence is controlled; threshold (sensitivity) is determined.

### 6.2 Approach 2: Confidence-First

**Given**: $\alpha$, desired power $(1-\beta)$, minimum detectable effect $\delta$, experimental basis

**Compute**: $n_{\text{test}}$

$$n_{\text{test}} = \left(\frac{z_\alpha \sqrt{\hat{p}_{\text{exp}}(1-\hat{p}_{\text{exp}})} + z_\beta \sqrt{(\hat{p}_{\text{exp}}-\delta)(1-\hat{p}_{\text{exp}}+\delta)}}{\delta}\right)^2$$

**Trade-off**: Fixed confidence and detection capability; cost (sample size) is determined.

### 6.3 Approach 3: Threshold-First

**Given**: $n_{\text{test}}$, $p_{\text{threshold}}$ (often = $\hat{p}_{\text{exp}}$), experimental basis

**Compute**: Implied $\alpha$

$$z_\alpha = \frac{\hat{p}_{\text{exp}} - p_{\text{threshold}}}{\sqrt{\hat{p}_{\text{exp}}(1-\hat{p}_{\text{exp}})/n_{\text{test}}}}$$

$$\alpha = 1 - \Phi(z_\alpha)$$

**Example**: Using threshold = 0.951 with $n = 100$:

$$z_\alpha = \frac{0.951 - 0.951}{0.0216} = 0$$
$$\alpha = 1 - \Phi(0) = 0.50$$

**Interpretation**: A 50% false positive rate—half of all test runs will fail even with no degradation.

**Trade-off**: Fixed cost and threshold; confidence (reliability of verdicts) is determined—often poorly.

---

## 7. Reporting and Interpretation

### 7.1 Test Failure Report

When $\hat{p}_{\text{test}} < p_{\text{threshold}}$, the report should include:

| Metric             | Value                                                        | Interpretation                         |
|--------------------|--------------------------------------------------------------|----------------------------------------|
| Observed rate      | $\hat{p}_{\text{test}}$                                      | Point estimate from test               |
| Threshold          | $p_{\text{threshold}}$                                       | Derived from experimental basis        |
| Shortfall          | $p_{\text{threshold}} - \hat{p}_{\text{test}}$               | Magnitude of deviation                 |
| Z-score            | $(\hat{p}_{\text{test}} - \hat{p}_{\text{exp}}) / \text{SE}$ | Standardized deviation                 |
| One-tailed p-value | $\Phi(z)$                                                    | Probability of observing this or worse |
| Confidence level   | $1 - \alpha$                                                 | Pre-specified false positive control   |

**Example**: Test observes 87/100 successes against threshold 0.916:

- Observed rate: 0.870
- Threshold: 0.916
- Shortfall: 0.046 (4.6 percentage points)
- Z-score: $(0.870 - 0.951) / 0.0216 = -3.75$
- p-value: $\Phi(-3.75) < 0.0001$
- Interpretation: Highly significant degradation; probability of this result under $H_0$ is < 0.01%.

### 7.2 Confidence Statement

Every failure report should include a plain-language confidence statement:

> "This test was configured with 95% confidence. There is a 5% probability that this failure is due to sampling variance rather than actual system degradation. The observed p-value of < 0.0001 indicates the result is highly unlikely under the null hypothesis of no degradation."

### 7.3 Multiple Testing Considerations

When running multiple probabilistic tests:

- **Per-test error rate**: Each test has false positive rate $\alpha$
- **Family-wise error rate**: Probability of at least one false positive increases with number of tests

For *m* independent tests at level $\alpha$:

$$P(\text{at least one false positive}) = 1 - (1-\alpha)^m$$

| Number of tests | Per-test α = 0.05 | Per-test α = 0.01 |
|-----------------|-------------------|-------------------|
| 5               | 22.6%             | 4.9%              |
| 10              | 40.1%             | 9.6%              |
| 20              | 64.2%             | 18.2%             |

**Mitigation options**:
- Bonferroni correction: Use $\alpha' = \alpha / m$
- Benjamini-Hochberg: Control false discovery rate
- Accept inflated family-wise rate with documentation

---

## 8. Assumptions and Validity Conditions

### 8.1 When Normal Approximation is Valid

The normal approximation to the binomial is adequate when:

$$n \cdot p \geq 5 \quad \text{and} \quad n \cdot (1-p) \geq 5$$

More conservatively (for confidence intervals):

$$n \cdot p \geq 10 \quad \text{and} \quad n \cdot (1-p) \geq 10$$

**For p = 0.95**:
- Need $n \geq 200$ for conservative criterion
- Wilson interval recommended for $n < 200$

### 8.2 Independence Violations

If trials are not independent, the effective sample size is reduced:

$$n_{\text{eff}} = \frac{n}{1 + (n-1)\rho}$$

where $\rho$ is the intraclass correlation.

**Detection**: Run autocorrelation analysis on trial outcomes. Significant lag-1 autocorrelation suggests dependence.

**Mitigation**: Increase sample size or introduce delays between trials.

### 8.3 Non-Stationarity

If *p* changes during the experiment:

- Point estimate $\hat{p}$ reflects time-averaged behavior
- Confidence intervals may understate true uncertainty
- Consider time-series analysis or segmented estimation

**Detection**: Plot success rate over time; test for trend using Cochran-Armitage or similar.

---

## 9. Summary of Key Formulas

### Estimation

$$\hat{p} = \frac{k}{n}, \quad \text{SE}(\hat{p}) = \sqrt{\frac{\hat{p}(1-\hat{p})}{n}}$$

### Wald Confidence Interval (two-sided)

$$\hat{p} \pm z_{\alpha/2} \cdot \text{SE}(\hat{p})$$

### Wilson Score Interval

$$\frac{\hat{p} + \frac{z^2}{2n} \pm z\sqrt{\frac{\hat{p}(1-\hat{p})}{n} + \frac{z^2}{4n^2}}}{1 + \frac{z^2}{n}}$$

### One-Sided Lower Bound (for threshold derivation)

$$p_{\text{threshold}} = \hat{p} - z_\alpha \cdot \text{SE}$$

### Wilson Lower Bound (for $\hat{p} = 1$)

$$p_{\text{lower}} = \frac{n}{n + z^2}$$

### Rule of Three (quick approximation for zero failures)

$$p \geq 1 - \frac{3}{n} \quad \text{(95% confidence)}$$

### Sample Size for Precision

$$n = \frac{z_{\alpha/2}^2 \cdot p(1-p)}{e^2}$$

### Sample Size for Power

$$n = \left(\frac{z_\alpha \sqrt{p_0(1-p_0)} + z_\beta \sqrt{p_1(1-p_1)}}{p_0 - p_1}\right)^2$$

---

## References

1. Wilson, E. B. (1927). Probable inference, the law of succession, and statistical inference. *Journal of the American Statistical Association*, 22(158), 209-212.

2. Agresti, A., & Coull, B. A. (1998). Approximate is better than "exact" for interval estimation of binomial proportions. *The American Statistician*, 52(2), 119-126.

3. Brown, L. D., Cai, T. T., & DasGupta, A. (2001). Interval estimation for a binomial proportion. *Statistical Science*, 16(2), 101-133.

4. Newcombe, R. G. (1998). Two-sided confidence intervals for the single proportion: comparison of seven methods. *Statistics in Medicine*, 17(8), 857-872.

5. Hanley, J. A., & Lippman-Hand, A. (1983). If nothing goes wrong, is everything all right? Interpreting zero numerators. *JAMA*, 249(13), 1743-1745. [The "Rule of Three"]

6. Clopper, C. J., & Pearson, E. S. (1934). The use of confidence or fiducial limits illustrated in the case of the binomial. *Biometrika*, 26(4), 404-413.

7. Gelman, A., Carlin, J. B., Stern, H. S., Dunson, D. B., Vehtari, A., & Rubin, D. B. (2013). *Bayesian Data Analysis* (3rd ed.). Chapman and Hall/CRC. [Beta-Binomial posterior predictive]

8. Jeffreys, H. (1946). An invariant form for the prior probability in estimation problems. *Proceedings of the Royal Society of London. Series A*, 186(1007), 453-461. [Jeffreys prior]

---

*This document is intended for review by professional statisticians. For operational guidance, see [Operational Flow](./OPERATIONAL-FLOW.md).*

