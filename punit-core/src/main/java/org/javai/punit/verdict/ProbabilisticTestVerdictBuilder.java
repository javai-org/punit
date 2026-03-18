package org.javai.punit.verdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.spec.expiration.ExpirationEvaluator;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.ComplianceEvidenceEvaluator;
import org.javai.punit.statistics.ProportionEstimate;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.transparent.BaselineData;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;

/**
 * Constructs {@link ProbabilisticTestVerdict} from the raw sources available after
 * probabilistic test execution completes.
 *
 * <p>This builder bridges the gap between the test execution engine's mutable state
 * (aggregator, budget monitors) and the immutable verdict model. It snapshots mutable
 * state, computes derived values (statistical inference), and organises everything into
 * the nested record structure.
 *
 * <p>The builder does not perform any rendering or formatting.
 */
public class ProbabilisticTestVerdictBuilder {

    private final BinomialProportionEstimator estimator = new BinomialProportionEstimator();

    // ── Envelope ──────────────────────────────────────────────────────────
    private String correlationId;
    private Map<String, String> environmentMetadata = Map.of();

    // ── Identity ──────────────────────────────────────────────────────────
    private String className;
    private String methodName;
    private String useCaseId;

    // ── Execution ─────────────────────────────────────────────────────────
    private int plannedSamples;
    private int samplesExecuted;
    private int successes;
    private int failures;
    private double minPassRate;
    private double observedPassRate;
    private long elapsedMs;
    private Double appliedMultiplier;
    private TestIntent intent = TestIntent.VERIFICATION;
    private double resolvedConfidence = 0.95;
    private UseCaseAttributes useCaseAttributes = UseCaseAttributes.DEFAULT;

    // ── Dimensions ────────────────────────────────────────────────────────
    private Integer functionalSuccesses;
    private Integer functionalFailures;
    private LatencyInput latencyInput;

    // ── Covariates ────────────────────────────────────────────────────────
    private List<MisalignmentInput> misalignments = List.of();

    // ── Cost ──────────────────────────────────────────────────────────────
    private long methodTokensConsumed;
    private long methodTimeBudgetMs;
    private long methodTokenBudget;
    private TokenMode tokenMode = TokenMode.NONE;
    private SharedBudgetMonitor classBudget;
    private SharedBudgetMonitor suiteBudget;

    // ── Pacing ────────────────────────────────────────────────────────────
    private PacingConfiguration pacingConfig;

    // ── Provenance ────────────────────────────────────────────────────────
    private ThresholdOrigin thresholdOrigin;
    private String contractRef;
    private String specFilename;
    private String baselineSourceLabel;
    private ExecutionSpecification spec;

    // ── Baseline ──────────────────────────────────────────────────────────
    private BaselineData baseline;

    // ── Termination ───────────────────────────────────────────────────────
    private TerminationReason terminationReason = TerminationReason.COMPLETED;
    private String terminationDetails;

    // ── Verdicts ──────────────────────────────────────────────────────────
    private boolean junitPassed;
    private boolean passedStatistically;

    // ── Builder methods ───────────────────────────────────────────────────

    public ProbabilisticTestVerdictBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public ProbabilisticTestVerdictBuilder environmentMetadata(Map<String, String> metadata) {
        this.environmentMetadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        return this;
    }

    public ProbabilisticTestVerdictBuilder identity(String className, String methodName, String useCaseId) {
        this.className = className;
        this.methodName = methodName;
        this.useCaseId = useCaseId;
        return this;
    }

    public ProbabilisticTestVerdictBuilder execution(int plannedSamples, int samplesExecuted,
                                                      int successes, int failures,
                                                      double minPassRate, double observedPassRate,
                                                      long elapsedMs) {
        this.plannedSamples = plannedSamples;
        this.samplesExecuted = samplesExecuted;
        this.successes = successes;
        this.failures = failures;
        this.minPassRate = minPassRate;
        this.observedPassRate = observedPassRate;
        this.elapsedMs = elapsedMs;
        return this;
    }

    public ProbabilisticTestVerdictBuilder appliedMultiplier(double multiplier) {
        this.appliedMultiplier = multiplier;
        return this;
    }

    public ProbabilisticTestVerdictBuilder intent(TestIntent intent, double resolvedConfidence) {
        this.intent = intent;
        this.resolvedConfidence = resolvedConfidence;
        return this;
    }

    public ProbabilisticTestVerdictBuilder useCaseAttributes(UseCaseAttributes useCaseAttributes) {
        this.useCaseAttributes = useCaseAttributes != null ? useCaseAttributes : UseCaseAttributes.DEFAULT;
        return this;
    }

    public ProbabilisticTestVerdictBuilder warmup(int warmup) {
        this.useCaseAttributes = new UseCaseAttributes(warmup, this.useCaseAttributes.maxConcurrent());
        return this;
    }

    public ProbabilisticTestVerdictBuilder functionalDimension(int successes, int failures) {
        this.functionalSuccesses = successes;
        this.functionalFailures = failures;
        return this;
    }

    public ProbabilisticTestVerdictBuilder latencyDimension(LatencyInput input) {
        this.latencyInput = input;
        return this;
    }

    public ProbabilisticTestVerdictBuilder misalignments(List<MisalignmentInput> misalignments) {
        this.misalignments = misalignments != null ? misalignments : List.of();
        return this;
    }

    public ProbabilisticTestVerdictBuilder cost(long methodTokensConsumed,
                                                 long methodTimeBudgetMs,
                                                 long methodTokenBudget,
                                                 TokenMode tokenMode) {
        this.methodTokensConsumed = methodTokensConsumed;
        this.methodTimeBudgetMs = methodTimeBudgetMs;
        this.methodTokenBudget = methodTokenBudget;
        this.tokenMode = tokenMode;
        return this;
    }

    public ProbabilisticTestVerdictBuilder sharedBudgets(SharedBudgetMonitor classBudget,
                                                          SharedBudgetMonitor suiteBudget) {
        this.classBudget = classBudget;
        this.suiteBudget = suiteBudget;
        return this;
    }

    public ProbabilisticTestVerdictBuilder pacing(PacingConfiguration pacingConfig) {
        this.pacingConfig = pacingConfig;
        return this;
    }

    public ProbabilisticTestVerdictBuilder provenance(ThresholdOrigin thresholdOrigin,
                                                       String contractRef,
                                                       String specFilename) {
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.specFilename = specFilename;
        return this;
    }

    public ProbabilisticTestVerdictBuilder provenance(ThresholdOrigin thresholdOrigin,
                                                       String contractRef,
                                                       String specFilename,
                                                       String baselineSourceLabel) {
        this.thresholdOrigin = thresholdOrigin;
        this.contractRef = contractRef;
        this.specFilename = specFilename;
        this.baselineSourceLabel = baselineSourceLabel;
        return this;
    }

    public ProbabilisticTestVerdictBuilder spec(ExecutionSpecification spec) {
        this.spec = spec;
        return this;
    }

    public ProbabilisticTestVerdictBuilder baseline(BaselineData baseline) {
        this.baseline = baseline;
        return this;
    }

    public ProbabilisticTestVerdictBuilder termination(TerminationReason reason, String details) {
        this.terminationReason = reason != null ? reason : TerminationReason.COMPLETED;
        this.terminationDetails = details;
        return this;
    }

    public ProbabilisticTestVerdictBuilder junitPassed(boolean junitPassed) {
        this.junitPassed = junitPassed;
        return this;
    }

    public ProbabilisticTestVerdictBuilder passedStatistically(boolean passed) {
        this.passedStatistically = passed;
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    public ProbabilisticTestVerdict build() {
        String id = correlationId != null ? correlationId : newCorrelationId();
        TestIdentity identity = buildIdentity();
        ExecutionSummary execution = buildExecutionSummary();
        Optional<FunctionalDimension> functional = buildFunctionalDimension();
        Optional<LatencyDimension> latency = buildLatencyDimension();
        CovariateStatus covariates = buildCovariateStatus();
        StatisticalAnalysis statistics = buildStatisticalAnalysis(covariates);
        CostSummary cost = buildCostSummary();
        Optional<PacingSummary> pacing = buildPacingSummary();
        Optional<SpecProvenance> provenance = buildSpecProvenance();
        Termination termination = buildTermination();
        PunitVerdict punitVerdict = derivePunitVerdict(covariates);

        return new ProbabilisticTestVerdict(
                id, Instant.now(),
                identity, execution, functional, latency, statistics,
                covariates, cost, pacing, provenance, termination,
                environmentMetadata, junitPassed, punitVerdict
        );
    }

    private static String newCorrelationId() {
        return "v:" + UUID.randomUUID().toString().substring(0, 6);
    }

    // ── Private builders ──────────────────────────────────────────────────

    private TestIdentity buildIdentity() {
        return new TestIdentity(
                className,
                methodName,
                Optional.ofNullable(useCaseId)
        );
    }

    private ExecutionSummary buildExecutionSummary() {
        return new ExecutionSummary(
                plannedSamples, samplesExecuted, successes, failures,
                minPassRate, observedPassRate, elapsedMs,
                Optional.ofNullable(appliedMultiplier),
                intent, resolvedConfidence, useCaseAttributes
        );
    }

    private Optional<FunctionalDimension> buildFunctionalDimension() {
        if (functionalSuccesses == null) {
            return Optional.empty();
        }
        int s = functionalSuccesses;
        int f = functionalFailures != null ? functionalFailures : 0;
        double rate = (s + f) > 0 ? (double) s / (s + f) : 0.0;
        return Optional.of(new FunctionalDimension(s, f, rate));
    }

    private Optional<LatencyDimension> buildLatencyDimension() {
        if (latencyInput == null) {
            return Optional.empty();
        }
        LatencyInput li = latencyInput;
        List<PercentileAssertion> assertions = li.assertions() != null
                ? li.assertions().stream()
                .map(a -> new PercentileAssertion(
                        a.label(), a.observedMs(), a.thresholdMs(),
                        a.passed(), a.indicative(), a.source()))
                .toList()
                : List.of();

        return Optional.of(new LatencyDimension(
                li.successfulSamples(), li.totalSamples(),
                li.skipped(), Optional.ofNullable(li.skipReason()),
                li.p50Ms(), li.p90Ms(), li.p95Ms(), li.p99Ms(), li.maxMs(),
                assertions, li.caveats() != null ? li.caveats() : List.of(),
                li.dimensionSuccesses(), li.dimensionFailures()
        ));
    }

    private CovariateStatus buildCovariateStatus() {
        if (misalignments.isEmpty()) {
            return CovariateStatus.allAligned();
        }
        List<Misalignment> converted = misalignments.stream()
                .map(m -> new Misalignment(m.covariateKey(), m.baselineValue(), m.testValue()))
                .toList();
        return new CovariateStatus(false, converted);
    }

    private StatisticalAnalysis buildStatisticalAnalysis(CovariateStatus covariates) {
        double confidenceLevel = resolvedConfidence;

        double standardError = samplesExecuted > 0
                ? Math.sqrt(observedPassRate * (1 - observedPassRate) / samplesExecuted)
                : 0.0;

        double ciLower = 0.0;
        double ciUpper = 0.0;
        if (samplesExecuted > 0) {
            ProportionEstimate estimate = estimator.estimate(successes, samplesExecuted, confidenceLevel);
            ciLower = estimate.lowerBound();
            ciUpper = estimate.upperBound();
        }

        Optional<Double> testStatistic = Optional.empty();
        Optional<Double> pValue = Optional.empty();
        if (samplesExecuted > 0) {
            double z = estimator.zTestStatistic(observedPassRate, minPassRate, samplesExecuted);
            testStatistic = Optional.of(z);
            pValue = Optional.of(estimator.oneSidedPValue(z));
        }

        Optional<String> thresholdDerivation = Optional.empty();
        Optional<BaselineSummary> baselineSummary = Optional.empty();
        if (baseline != null && baseline.hasEmpiricalData()) {
            thresholdDerivation = Optional.of(buildThresholdDerivation());
            baselineSummary = Optional.of(new BaselineSummary(
                    baseline.sourceFile(),
                    baseline.generatedAt(),
                    baseline.baselineSamples(),
                    baseline.baselineSuccesses(),
                    baseline.baselineRate(),
                    minPassRate
            ));
        }

        List<String> caveats = buildCaveats(covariates);

        return new StatisticalAnalysis(
                confidenceLevel, standardError, ciLower, ciUpper,
                testStatistic, pValue, thresholdDerivation, baselineSummary, caveats
        );
    }

    private String buildThresholdDerivation() {
        if (baseline == null || !baseline.hasEmpiricalData()) {
            return "Inline threshold (no baseline spec)";
        }
        return "Wilson";
    }

    private List<String> buildCaveats(CovariateStatus covariates) {
        List<String> caveats = new ArrayList<>();

        if (!covariates.aligned()) {
            caveats.add("Covariate mismatch: test conditions do not match baseline conditions. "
                    + "Results may not be comparable.");
        }

        String originName = thresholdOrigin != null ? thresholdOrigin.name() : null;
        boolean isSmoke = intent == TestIntent.SMOKE;

        if (!isSmoke && ComplianceEvidenceEvaluator.hasComplianceContext(originName, contractRef)) {
            if (ComplianceEvidenceEvaluator.isUndersized(samplesExecuted, minPassRate)) {
                caveats.add(ComplianceEvidenceEvaluator.SIZING_NOTE);
            }
        }

        if (isSmoke && thresholdOrigin != null && thresholdOrigin.isNormative()) {
            double target = minPassRate;
            if (!Double.isNaN(target) && target > 0.0 && target < 1.0) {
                var result = VerificationFeasibilityEvaluator.evaluate(
                        samplesExecuted, target, resolvedConfidence);
                if (!result.feasible()) {
                    caveats.add(String.format(
                            "Sample not sized for verification (N=%d, need %d for target at %.0f%% confidence).",
                            samplesExecuted, result.minimumSamples(), resolvedConfidence * 100));
                }
            }
        }

        return List.copyOf(caveats);
    }

    private CostSummary buildCostSummary() {
        Optional<BudgetSnapshot> classSnapshot = classBudget != null
                ? Optional.of(snapshotBudget(classBudget))
                : Optional.empty();

        Optional<BudgetSnapshot> suiteSnapshot = suiteBudget != null
                ? Optional.of(snapshotBudget(suiteBudget))
                : Optional.empty();

        return new CostSummary(
                methodTokensConsumed, methodTimeBudgetMs, methodTokenBudget,
                tokenMode, classSnapshot, suiteSnapshot
        );
    }

    private BudgetSnapshot snapshotBudget(SharedBudgetMonitor monitor) {
        return new BudgetSnapshot(
                monitor.getTimeBudgetMs(),
                monitor.getElapsedMs(),
                monitor.getTokenBudget(),
                monitor.getTokensConsumed()
        );
    }

    private Optional<PacingSummary> buildPacingSummary() {
        if (pacingConfig == null || !pacingConfig.hasPacing()) {
            return Optional.empty();
        }
        return Optional.of(new PacingSummary(
                pacingConfig.maxRequestsPerSecond(),
                pacingConfig.maxRequestsPerMinute(),
                pacingConfig.maxRequestsPerHour(),
                pacingConfig.maxConcurrentRequests(),
                pacingConfig.effectiveMinDelayMs(),
                pacingConfig.effectiveConcurrency(),
                pacingConfig.effectiveRps()
        ));
    }

    private Optional<SpecProvenance> buildSpecProvenance() {
        if (thresholdOrigin == null || thresholdOrigin == ThresholdOrigin.UNSPECIFIED) {
            if (contractRef == null || contractRef.isEmpty()) {
                return Optional.empty();
            }
        }
        Optional<ExpirationInfo> expiration = buildExpirationInfo();

        return Optional.of(new SpecProvenance(
                thresholdOrigin != null ? thresholdOrigin.name() : "UNSPECIFIED",
                contractRef,
                specFilename,
                expiration,
                Optional.ofNullable(baselineSourceLabel)
        ));
    }

    private Optional<ExpirationInfo> buildExpirationInfo() {
        if (spec == null) {
            return Optional.empty();
        }
        ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
        Optional<Instant> expiresAt = Optional.empty();
        if (spec.hasExpirationPolicy() && spec.getExpirationPolicy() != null) {
            expiresAt = spec.getExpirationPolicy().expirationTime();
        }
        return Optional.of(new ExpirationInfo(status, expiresAt));
    }

    private Termination buildTermination() {
        return new Termination(terminationReason, Optional.ofNullable(terminationDetails));
    }

    private PunitVerdict derivePunitVerdict(CovariateStatus covariates) {
        if (!covariates.aligned()) {
            return PunitVerdict.INCONCLUSIVE;
        }
        return passedStatistically ? PunitVerdict.PASS : PunitVerdict.FAIL;
    }

    // ── Input records ─────────────────────────────────────────────────────

    /**
     * Primitive-only input for latency dimension data. Avoids coupling to
     * engine-internal types like {@code LatencyAssertionResult}.
     */
    public record LatencyInput(
            int successfulSamples,
            int totalSamples,
            boolean skipped,
            String skipReason,
            long p50Ms,
            long p90Ms,
            long p95Ms,
            long p99Ms,
            long maxMs,
            List<PercentileAssertionInput> assertions,
            List<String> caveats,
            int dimensionSuccesses,
            int dimensionFailures
    ) {}

    /**
     * Primitive-only input for a single percentile assertion.
     */
    public record PercentileAssertionInput(
            String label,
            long observedMs,
            long thresholdMs,
            boolean passed,
            boolean indicative,
            String source
    ) {}

    /**
     * Primitive-only input for a covariate misalignment.
     */
    public record MisalignmentInput(
            String covariateKey,
            String baselineValue,
            String testValue
    ) {}
}
