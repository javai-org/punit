# REQ: Efficient Experiment Sample Size Planning

## Status

Open

## Context

### The problem

A `@MeasureExperiment` requires the user to specify a sample count. The current
guidance is "use a large number" — typically 1000. But how large is large
enough?

- **Too few samples** produce wide confidence intervals. The derived threshold
  is unstable: run the experiment again and you may get a materially different
  spec. The resulting `@ProbabilisticTest` may be over- or under-sensitive.
- **Too many samples** waste time and money. LLM calls cost real currency per
  invocation. A 10,000-sample experiment on a GPT-4-class model can take hours
  and cost hundreds of dollars — yet the last 5,000 samples may not meaningfully
  narrow the confidence interval.

The framework already has the statistical machinery to answer this question.
`SampleSizeCalculator` computes required n from power analysis.
`BinomialProportionEstimator` computes Wilson confidence intervals.
`VerificationFeasibilityEvaluator` computes N_min for verification intent. But
none of these are connected to the measure experiment workflow in a way that
helps the user *plan* sample size before running, or *stop early* when
sufficient precision has been achieved.

### The statistical basis

PUnit models outcomes as Bernoulli trials with unknown success probability p.
The precision of the estimate p-hat depends on:

1. **The true pass rate p** — variance is p(1-p), maximised at p = 0.5.
   A system that passes 95% of the time needs far fewer samples than one that
   passes 50% of the time, because the population variance is lower.
2. **The sample size n** — standard error is sqrt(p(1-p)/n), so precision
   improves as 1/sqrt(n). Doubling precision requires quadrupling samples.
3. **The desired confidence level** — higher confidence requires more samples
   (wider z-score multiplier).

The key insight: **if we can estimate p early, we can compute the sample size
needed to achieve a target precision**. We do not need to guess.

### What exists today

| Component | What it does | Gap |
|-----------|-------------|-----|
| `SampleSizeCalculator` | Power analysis: given p0, delta, alpha, beta, computes n | Not connected to measure workflow. Requires the user to know p0 upfront. |
| `BinomialProportionEstimator` | Wilson CI computation | Used post-hoc in `InferentialStatistics`, not monitored during execution. |
| `VerificationFeasibilityEvaluator` | N_min for verification intent | Pre-execution gate, but only for `@ProbabilisticTest`, not `@MeasureExperiment`. |
| `EarlyTerminationEvaluator` | Deterministic impossibility/success-guaranteed | No precision-based stopping. Only checks pass/fail boundaries. |
| `MeasureStrategy` | Executes measure samples with budget checks | Runs to completion (or budget exhaustion). No precision monitoring. |
| `InferentialStatistics` | Records CI, SE, margin of error | Computed after all samples complete. Not consulted during execution. |

---

## 1. Goals

Enable users to run measure experiments with **statistically grounded sample
sizes** rather than arbitrary round numbers — reducing cost without sacrificing
the precision needed to produce reliable specs.

Specifically:

1. **Pre-experiment advisory**: given a precision target, tell the user how many
   samples to plan for.
2. **Pilot-based estimation**: when the pass rate is unknown, use a small
   initial sample to estimate variability and compute the required n.
3. **Precision-based early stopping**: during a measure experiment, monitor CI
   width and stop when sufficient precision has been achieved — even if the
   planned sample count has not been exhausted.
4. **Post-experiment adequacy assessment**: after a measure experiment, report
   whether the achieved precision is adequate for the spec's intended use.

---

## 2. Design Principles

### 2.1 Advisory, not mandatory

Sample size planning should *inform* the user, not constrain them. A user who
specifies `samples = 500` should not be blocked by a framework opinion that they
need 800. The framework may warn, but must not refuse.

This aligns with PUnit's philosophy: the framework provides statistical
discipline; the user retains control.

### 2.2 Conservative defaults

When estimating required sample size without prior knowledge, use worst-case
variance (p = 0.5). This guarantees the recommended n is sufficient regardless
of the true pass rate. Users who have prior knowledge can supply it to get a
tighter (smaller) estimate.

### 2.3 Precision over power

For measure experiments, the primary concern is **estimation precision** (how
narrow is the confidence interval around p-hat), not **hypothesis testing power**
(can we detect a delta-sized degradation). Power analysis
(`SampleSizeCalculator`) is the right tool for verification-intent
`@ProbabilisticTest`. Precision targeting is the right tool for
`@MeasureExperiment`.

The distinction:
- **Verification**: "Can I detect a 5% degradation with 90% power?" → power
  analysis → `SampleSizeCalculator`
- **Measurement**: "How many samples until my CI is within +/-2%?" → precision
  targeting → new capability

### 2.4 Transparency

All sample size recommendations must be explainable. The framework must report
the formula used, the inputs (estimated p, target margin, confidence level),
and the resulting n. No black boxes.

---

## 3. Precision Targeting

### 3.1 The formula

For a Wilson score interval, the half-width (margin of error) is approximately:

```
E ≈ z * sqrt(p(1-p) / n)
```

Solving for n:

```
n = z² * p(1-p) / E²
```

where:
- z = z-score for the desired confidence level (1.96 for 95%)
- p = estimated success probability (or 0.5 if unknown)
- E = target margin of error (half-width of the CI)

### 3.2 Examples

| True p | Target margin E | Confidence | Required n |
|--------|----------------|------------|------------|
| 0.50   | ±0.05          | 95%        | 385        |
| 0.50   | ±0.02          | 95%        | 2,401      |
| 0.90   | ±0.05          | 95%        | 139        |
| 0.90   | ±0.02          | 95%        | 865        |
| 0.95   | ±0.02          | 95%        | 457        |
| 0.95   | ±0.01          | 95%        | 1,825      |
| 0.50   | ±0.05          | 99%        | 664        |

The practical implication: a system with a high pass rate (p ≈ 0.95) needs
roughly one-quarter the samples of a maximally uncertain system (p ≈ 0.50) for
the same precision. Estimating p early pays for itself.

### 3.3 Relationship to existing `SampleSizeCalculator`

`SampleSizeCalculator` answers a different question: "How many samples to
*detect a degradation* of size delta with power 1-beta?" This is a two-sample
hypothesis-testing framing appropriate for verification.

Precision targeting answers: "How many samples to *estimate p* within margin E?"
This is a single-sample estimation framing appropriate for measurement.

Both are valid. Both should be available. They serve different intents.

---

## 4. Pilot Sampling

### 4.1 The idea

When the pass rate is entirely unknown, run a small **pilot phase** to estimate
p-hat, then use that estimate to compute the remaining samples needed.

### 4.2 Two-stage design

1. **Stage 1 (pilot)**: Run n_pilot samples (e.g., 30-50). Compute p-hat_pilot
   and Wilson CI.
2. **Compute required n**: Using p-hat_pilot, target margin E, and confidence
   level, compute total n required.
3. **Stage 2 (completion)**: Run max(0, n_required - n_pilot) additional
   samples. The pilot samples count toward the total — they are not discarded.

### 4.3 Pilot size

The pilot must be large enough to produce a usable estimate of p. Guidance:

- **Minimum 30 samples** — standard statistical rule of thumb for the normal
  approximation to be reasonable.
- **For rare events (p < 0.05 or p > 0.95)**: 30 may not observe enough
  failures/successes. Consider 50-100, or flag that pilot precision is limited.

The pilot size need not be large because it only needs to distinguish "p is near
0.5" from "p is near 0.95" — the former requires ~4x the samples of the latter,
so even a rough estimate saves significant resources.

### 4.4 Safety: overestimation is cheap, underestimation is not

If the pilot overestimates variance (e.g., p-hat_pilot = 0.80 when true p =
0.90), the computed n will be larger than strictly necessary — the user runs
extra samples but gets a narrower CI than targeted. This is acceptable waste.

If the pilot underestimates variance (e.g., p-hat_pilot = 0.95 when true p =
0.85), the computed n will be too small — the user gets a wider CI than
targeted. This is a quality risk.

Mitigation: use the **lower bound of the pilot CI for p** when computing the
variance term p(1-p). Since p(1-p) is maximised near 0.5, using a conservative
(toward 0.5) estimate of p inflates the variance estimate and produces a larger
n — erring on the side of more samples. Specifically:

- If p-hat > 0.5, use the lower bound of the pilot CI (closer to 0.5, higher
  variance).
- If p-hat < 0.5, use the upper bound of the pilot CI (closer to 0.5, higher
  variance).
- If p-hat ≈ 0.5, it doesn't matter — variance is near-maximal regardless.

### 4.5 Integration with measure workflow

> **[OPEN QUESTION]** Should pilot sampling be:
>
> (a) **Transparent within `@MeasureExperiment`**: the user sets
>     `samples = "auto"` or `targetPrecision = 0.02`, and the framework
>     internally runs a pilot phase then continues to the computed n.
>
> (b) **A separate pre-experiment step**: the user runs a pilot experiment
>     first, gets a recommended n, then runs the full measure experiment with
>     that n.
>
> (c) **Both**: auto mode for convenience, separate step for users who want
>     explicit control.
>
> Option (a) is more convenient but less transparent. Option (b) is more
> explicit but requires two runs. Option (c) adds API surface. PUnit's design
> principle of explicit approximation (REQ-DOE §2.2) suggests (b) or (c).

---

## 5. Precision-Based Early Stopping

### 5.1 The idea

During a measure experiment, monitor the Wilson CI width after each sample (or
batch of samples). If the CI narrows to within the target margin before all
planned samples have been executed, stop early.

This is the complement of pilot sampling: pilot sampling computes n upfront;
precision-based stopping monitors precision online and terminates when the
target is met.

### 5.2 Mechanism

After every k samples (where k is a configurable check interval, e.g., every
50 samples):

1. Compute Wilson CI for the current p-hat and n_so_far.
2. Compute margin of error = (CI_upper - CI_lower) / 2.
3. If margin <= target margin E, stop. Report that precision target was
   achieved.
4. Otherwise, continue.

### 5.3 Interaction with existing early termination

`EarlyTerminationEvaluator` currently handles deterministic boundaries
(impossibility, success guaranteed) for `@ProbabilisticTest`. Precision-based
stopping is a different mechanism for a different context (`@MeasureExperiment`).
These should be separate concerns:

- `EarlyTerminationEvaluator` — deterministic pass/fail boundary checks
  (verification)
- New: precision monitor — CI width monitoring (measurement)

### 5.4 Interaction with budgets

Precision-based stopping adds a new termination condition alongside existing
budget checks (time, token). The first condition to trigger wins:

- Precision achieved → stop (success — CI is narrow enough)
- Time budget exhausted → stop (budget — report achieved precision)
- Token budget exhausted → stop (budget — report achieved precision)
- All planned samples complete → stop (plan — report achieved precision)

In all cases, report the achieved precision so the user can assess adequacy.

### 5.5 Minimum samples before precision checks

Do not check precision in the first n_min samples (e.g., first 30). Wilson
intervals are well-behaved at small n, but the point estimate p-hat is volatile.
Stopping at n=15 because the CI happens to be narrow (due to an extreme early
run of successes) produces an unreliable estimate.

---

## 6. Post-Experiment Adequacy Assessment

### 6.1 The idea

After a measure experiment completes, assess whether the achieved precision is
adequate for the spec's intended downstream use.

### 6.2 Adequacy criteria

A measure experiment produces an `ExecutionSpecification` with a baseline pass
rate and sample count. This spec is later consumed by `@ProbabilisticTest` via
`ThresholdDeriver`, which derives a threshold from the baseline using Wilson
lower bounds.

The derived threshold is sensitive to the precision of the baseline estimate.
If the baseline CI is wide, the derived threshold will be conservative (lower
than necessary), making the test less sensitive to real degradation.

Adequacy can be defined as: **the baseline margin of error is small enough that
the derived threshold is stable under resampling**. Concretely: if the
experiment were repeated, the derived threshold would change by less than some
tolerance (e.g., 1 percentage point).

### 6.3 Reporting

After a measure experiment, report:

```
Precision assessment:
  Observed pass rate:  0.923
  Wilson 95% CI:       [0.905, 0.938]
  Margin of error:     ±0.017
  Derived threshold:   0.893 (at 95% confidence)
  Threshold stability: ±0.008 under resampling
  Verdict:             Adequate — CI is narrow relative to threshold gap
```

Or, if inadequate:

```
Precision assessment:
  Observed pass rate:  0.91
  Wilson 95% CI:       [0.84, 0.96]
  Margin of error:     ±0.06
  Derived threshold:   0.810 (at 95% confidence)
  Threshold stability: ±0.035 under resampling
  Verdict:             Insufficient — consider increasing samples to ~500
                       (estimated for ±0.02 margin)
```

---

## 7. API Surface

> **[OPEN QUESTION]** The following sketches illustrate possible API shapes. They
> are not commitments. The design phase should evaluate alternatives.

### 7.1 Pre-experiment advisory

```java
// "I think my pass rate is about 0.9. How many samples for ±2% margin?"
int n = PrecisionPlanner.requiredSamples(
    estimatedRate: 0.9,
    targetMargin: 0.02,
    confidence: 0.95
);
// → 865

// "I have no idea what my pass rate is. Give me worst case."
int n = PrecisionPlanner.requiredSamples(
    targetMargin: 0.02,
    confidence: 0.95
);
// → 2401 (assumes p = 0.5)
```

### 7.2 Precision-targeted measure experiment

```java
@MeasureExperiment(
    targetMargin = 0.02,      // stop when CI half-width <= 0.02
    confidence = 0.95,
    maxSamples = 5000,        // safety cap
    pilotSamples = 50         // initial batch before precision checks begin
)
```

### 7.3 Pilot-then-plan (two-step)

```java
// Step 1: run pilot
@MeasureExperiment(samples = 50)
void pilot(MyUseCase useCase, OutcomeCaptor captor) { ... }

// Step 2: inspect pilot spec, compute required n
// (framework reports: "Observed p=0.92 with CI [0.81, 0.97].
//  For ±0.02 margin at 95%: recommend 707 samples.")

// Step 3: run full measure with recommended n
@MeasureExperiment(samples = 707)
void measure(MyUseCase useCase, OutcomeCaptor captor) { ... }
```

---

## 8. Interaction with DoE

When a measure experiment uses factor configurations (via `@FactorSource` and
design matrices from REQ-DOE), the sample size question applies **per
configuration**. Total experiment cost = configurations x samples per config.

This makes sample size efficiency even more important: a full factorial design
with 6 configurations and 1000 samples each is 6,000 invocations. If
precision-targeted planning shows that 400 samples per config suffices (because
p ≈ 0.93 for most configs), the saving is 3,600 invocations.

> **[OPEN QUESTION]** Should precision targeting apply uniformly across all
> configurations, or per-configuration? Different factor combinations may
> produce different pass rates (and therefore different variances). Uniform
> allocation is simpler; per-config adaptive allocation is more efficient but
> significantly more complex.
>
> Recommendation for initial implementation: uniform allocation with a
> conservative (worst-case across configs) sample size. Per-config adaptive
> allocation is a natural extension for REQ-DOE §4.2 "adaptive allocation."

---

## 9. Non-Goals

- **Sequential probability ratio tests (SPRT)**: SPRT is designed for
  hypothesis testing (accept/reject), not estimation. PUnit's measure intent is
  estimation. SPRT may be relevant for verification intent in the future, but is
  out of scope here.
- **Bayesian sample size determination**: Bayesian methods (e.g., credible
  interval targeting with informative priors) are a valid alternative but
  introduce prior specification complexity. PUnit uses frequentist methods
  (Wilson score) throughout. Consistency favours staying frequentist.
- **Multi-endpoint power analysis**: When a use case has multiple outcome
  dimensions, sample size planning for joint coverage is a research-level
  problem. This requirement covers single-proportion estimation only.

---

## 10. Success Criteria

- Users can compute a recommended sample size for a target precision without
  running an experiment (pre-experiment advisory).
- Users can run a pilot experiment and receive a recommended sample size for a
  full experiment based on observed variability.
- Measure experiments can terminate early when a precision target is achieved,
  with the early stop reported transparently.
- Post-experiment output includes a precision assessment indicating whether the
  achieved CI is adequate for reliable spec generation.
- All recommendations are explainable: formula, inputs, and result are reported.
- Existing measure experiments with explicit `samples = N` continue to work
  unchanged (full backward compatibility).

---

## 11. Implementation Considerations

### Where does this live?

- `PrecisionPlanner` (or similar) → `punit-core` (statistics package). Pure
  computation, no JUnit dependency.
- Precision monitoring during execution → `punit-junit5` (within
  `MeasureStrategy`). Requires access to the sample stream.
- Post-experiment assessment → `punit-core` (experiment output package).
  Extends `InferentialStatistics` or adds a companion.

### Relationship to existing classes

| New capability | Builds on | In |
|---------------|-----------|-----|
| Pre-experiment n computation | `BinomialProportionEstimator` (Wilson formula) | `punit-core` |
| Pilot variance estimation | `BinomialProportionEstimator.estimate()` | `punit-core` |
| Precision monitoring | `BinomialProportionEstimator` + new monitor | `punit-junit5` |
| Adequacy assessment | `InferentialStatistics`, `ThresholdDeriver` | `punit-core` |

### Testing strategy

- Unit tests for `PrecisionPlanner`: known inputs → expected n (table-driven).
- Unit tests for pilot variance estimation: simulated pilot → computed n within
  expected range.
- Integration tests via TestKit: measure experiment with `targetMargin` →
  verify early stop occurs when CI is narrow enough.
- Property tests: for any p in (0,1) and E > 0, the recommended n produces a
  Wilson CI with half-width <= E (verified by simulation).
