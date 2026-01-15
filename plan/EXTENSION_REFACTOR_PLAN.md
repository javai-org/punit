# ProbabilisticTestExtension Refactoring Plan

## Problem Statement

`ProbabilisticTestExtension` has grown to 1744 lines and spans too many responsibilities. As a JUnit5 extension, it should primarily focus on implementing `TestTemplateInvocationContextProvider` and `InvocationInterceptor` interfaces—the orchestration layer—rather than containing detailed implementation logic.

This plan extracts cohesive responsibilities into focused, package-private helper classes, each with its own unit tests.

---

## Design Principles

1. **Single Responsibility**: Each helper class handles exactly one cohesive concern
2. **Consistent Abstraction Level**: `ProbabilisticTestExtension` becomes a thin orchestration layer
3. **Package-Private Visibility**: Helpers are internal implementation details, not public API
4. **Testability**: Each extracted class gets its own test class
5. **Incremental Extraction**: One responsibility per phase, with tests passing at each step

---

## Identified Responsibilities

| Responsibility                   | Current Lines (approx) | Extraction Candidate            |
|----------------------------------|------------------------|---------------------------------|
| Configuration logging            | ~50                    | `FinalConfigurationLogger`      |
| Sample failure formatting        | ~40                    | `SampleFailureFormatter`        |
| Budget orchestration             | ~150                   | `BudgetOrchestrator`            |
| Sample execution lifecycle       | ~70                    | `SampleExecutor`                |
| Baseline selection               | ~300                   | `BaselineSelectionOrchestrator` |
| Result publishing & reporting    | ~200                   | `ResultPublisher`               |
| Inner `TestConfiguration` record | ~110                   | Move to own file                |
| Inner context/resolver records   | ~40                    | Move to own files               |

**Estimated reduction**: 850+ lines from main extension class

---

## The `interceptTestTemplateMethod` Problem

The main interception method is currently a 120-line "God method" handling:

1. Baseline resolution
2. Component retrieval (6 store lookups)
3. Pre-sample budget checks
4. Token recorder reset
5. Pacing delay application
6. Sample execution with exception handling
7. Token recording & propagation
8. Post-sample budget checks
9. Early termination evaluation
10. Test finalization
11. Failure re-throwing for IDE display

**Target state after refactoring:**

```java
@Override
public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> context,
                                        ExtensionContext extensionContext) throws Throwable {
    
    SampleContext ctx = sampleContextResolver.resolve(extensionContext);
    
    if (ctx.isTerminated()) {
        invocation.skip();
        return;
    }
    
    // Pre-sample budget check
    var budgetResult = budgetOrchestrator.checkBeforeSample(ctx.budgets());
    if (budgetResult.shouldTerminate()) {
        handleTermination(ctx, budgetResult.reason(), budgetResult.behavior());
        invocation.skip();
        return;
    }
    
    // Execute sample
    SampleResult result = sampleExecutor.execute(invocation, ctx);
    
    // Post-sample processing
    budgetOrchestrator.recordTokens(ctx.budgets(), result.tokensConsumed());
    
    // Check for termination (budget, impossibility, or completion)
    var terminationCheck = terminationEvaluator.evaluate(ctx, result);
    if (terminationCheck.shouldFinalize()) {
        testFinalizer.finalize(ctx, terminationCheck);
    }
    
    // Re-throw for IDE display if sample failed but test continues
    if (result.hasSampleFailure() && !ctx.isTerminated()) {
        throw sampleFailureFormatter.formatForIde(result, ctx);
    }
}
```

This reduces the method from 120 lines to ~30 lines of pure orchestration.

---

## Refactoring Phases

### Phase 1: Extract `FinalConfigurationLogger`

**Branch**: `refactor/extract-configuration-logger`

**What it does**: Formats and logs the test configuration block shown at test start.

**Methods to extract**:
- `logFinalConfiguration(ExtensionContext context)` → becomes the main entry point

**New class signature**:
```java
class FinalConfigurationLogger {
    FinalConfigurationLogger(PUnitReporter reporter);
    
    void log(String testName, TestConfiguration config);
}
```

**Testing focus**:
- Verify correct mode detection (SLA-DRIVEN, SPEC-DRIVEN, EXPLICIT THRESHOLD)
- Verify threshold formatting includes correct provenance
- Verify contract reference appears when specified

**Complexity**: Low
**Risk**: Low

---

### Phase 2: Extract `SampleFailureFormatter`

**Branch**: `refactor/extract-sample-failure-formatter`

**What it does**: Formats exception messages for individual sample failures, including the verdict hint.

**Methods to extract**:
- `formatVerdictHint(SampleResultAggregator, TestConfiguration)`
- `extractFailureReason(Throwable)`

**New class signature**:
```java
class SampleFailureFormatter {
    String formatVerdictHint(int successes, int executed, int planned, double threshold);
    String extractFailureReason(Throwable failure);
    String formatSampleFailure(Throwable failure, int successes, int executed, int planned, double threshold);
}
```

**Testing focus**:
- Verify hint format includes sample counts and threshold
- Verify multi-line assertion messages are truncated properly
- Verify null/blank messages produce sensible output

**Complexity**: Low
**Risk**: Low

---

### Phase 3: Extract `BudgetOrchestrator`

**Branch**: `refactor/extract-budget-orchestrator`

**What it does**: Coordinates budget checking across suite, class, and method scopes.

**Methods to extract**:
- `checkAllBudgetsBeforeSample(...)`
- `checkAllBudgetsAfterSample(...)`
- `recordAndPropagateTokens(...)`
- `determineBehavior(...)`
- `handleBudgetExhaustion(...)` (partial - verdict decision stays in extension)
- `buildBudgetExhaustionMessage(...)`
- `buildBudgetExhaustionFailureMessage(...)`

**New class signature**:
```java
class BudgetOrchestrator {
    record BudgetCheckResult(
        Optional<TerminationReason> terminationReason,
        BudgetExhaustedBehavior behavior
    ) {}
    
    BudgetCheckResult checkBeforeSample(
        SharedBudgetMonitor suiteBudget,
        SharedBudgetMonitor classBudget,
        CostBudgetMonitor methodBudget
    );
    
    BudgetCheckResult checkAfterSample(
        SharedBudgetMonitor suiteBudget,
        SharedBudgetMonitor classBudget,
        CostBudgetMonitor methodBudget
    );
    
    long recordAndPropagateTokens(
        DefaultTokenChargeRecorder tokenRecorder,
        CostBudgetMonitor methodBudget,
        CostBudgetMonitor.TokenMode tokenMode,
        int tokenCharge,
        SharedBudgetMonitor classBudget,
        SharedBudgetMonitor suiteBudget
    );
    
    String buildExhaustionMessage(
        TerminationReason reason,
        CostBudgetMonitor methodBudget,
        SharedBudgetMonitor classBudget,
        SharedBudgetMonitor suiteBudget
    );
    
    String buildExhaustionFailureMessage(
        SampleResultAggregator aggregator,
        int plannedSamples,
        double minPassRate
    );
}
```

**Testing focus**:
- Verify suite-level budget checked first (precedence)
- Verify token propagation across all scopes
- Verify exhaustion messages contain correct budget values

**Complexity**: Medium
**Risk**: Medium

---

### Phase 4: Extract `SampleExecutor`

**Branch**: `refactor/extract-sample-executor`

**What it does**: Encapsulates the execution of a single sample, including token recorder management, pacing, and exception handling policy.

**Logic to extract from `interceptTestTemplateMethod`**:
- Token recorder reset before sample
- Pacing delay application
- Sample invocation with try/catch
- Success/failure recording to aggregator
- Exception handling policy (ABORT_TEST vs FAIL_SAMPLE)

**New class signature**:
```java
class SampleExecutor {
    record SampleResult(
        boolean success,
        Throwable failure,      // null if success
        long tokensConsumed,
        boolean shouldAbort     // true if exception + ABORT_TEST policy
    ) {}
    
    SampleExecutor(PacingResolver pacingResolver);
    
    SampleResult execute(
        Invocation<Void> invocation,
        SampleResultAggregator aggregator,
        DefaultTokenChargeRecorder tokenRecorder,
        PacingConfiguration pacing,
        ExceptionHandling exceptionPolicy,
        AtomicInteger sampleCounter
    );
}
```

**Testing focus**:
- Verify success is recorded on clean execution
- Verify failure is recorded on AssertionError
- Verify ABORT_TEST policy triggers abort flag
- Verify FAIL_SAMPLE policy allows continuation
- Verify pacing delay applied for samples > 1
- Verify token recorder reset called

**Complexity**: Medium
**Risk**: Medium (touches critical execution path)

---

### Phase 5: Extract `BaselineSelectionOrchestrator`

**Branch**: `refactor/extract-baseline-orchestrator`

**What it does**: Handles lazy baseline selection, covariate resolution, and minPassRate derivation.

**Methods to extract**:
- `prepareBaselineSelection(...)`
- `ensureBaselineSelected(...)`
- `performBaselineSelection(...)`
- `logBaselineSelectionResult(...)`
- `deriveMinPassRateFromBaseline(...)`
- `findUseCaseProvider(...)`
- `extractMisalignments(...)`
- `loadBaselineDataFromContext(...)`
- `validateTestConfiguration(...)` (delegation)

**Inner types to move**:
- `PendingBaselineSelection` record

**New class signature**:
```java
class BaselineSelectionOrchestrator {
    BaselineSelectionOrchestrator(
        ConfigurationResolver configResolver,
        BaselineRepository baselineRepository,
        BaselineSelector baselineSelector,
        CovariateProfileResolver covariateProfileResolver,
        FootprintComputer footprintComputer,
        UseCaseCovariateExtractor covariateExtractor,
        ProbabilisticTestValidator testValidator,
        PUnitReporter reporter
    );
    
    record SelectionOutcome(
        ExecutionSpecification spec,
        SelectionResult selectionResult,
        double derivedMinPassRate,  // NaN if not derived
        List<CovariateMisalignment> misalignments
    ) {}
    
    void prepareSelection(
        ProbabilisticTest annotation,
        String specId,
        ExtensionContext.Store store
    );
    
    SelectionOutcome resolveSelection(
        ExtensionContext context,
        ExtensionContext.Store store
    );
    
    BaselineData loadBaselineData(
        ExecutionSpecification spec,
        SelectionResult result
    );
}
```

**Testing focus**:
- Verify inline threshold mode skips covariate selection
- Verify minPassRate derivation from baseline
- Verify validation is called with selected baseline
- Verify misalignment extraction

**Complexity**: High
**Risk**: Medium (well-defined boundaries)

---

### Phase 6: Extract `ResultPublisher`

**Branch**: `refactor/extract-result-publisher`

**What it does**: Publishes test results via TestReporter and prints console summaries.

**Methods to extract**:
- `publishResults(...)`
- `printConsoleSummary(...)`
- `printTransparentStatsSummary(...)`
- `printExpirationWarning(...)`
- `appendProvenance(...)`

**New class signature**:
```java
class ResultPublisher {
    ResultPublisher(PUnitReporter reporter);
    
    void publish(
        ExtensionContext context,
        SampleResultAggregator aggregator,
        TestConfiguration config,
        CostBudgetMonitor methodBudget,
        SharedBudgetMonitor classBudget,
        SharedBudgetMonitor suiteBudget,
        boolean passed,
        ExecutionSpecification spec,
        SelectionResult selectionResult
    );
    
    void printConsoleSummary(
        String testName,
        SampleResultAggregator aggregator,
        TestConfiguration config,
        boolean passed,
        Optional<TerminationReason> terminationReason
    );
    
    void printTransparentStatsSummary(
        String testName,
        SampleResultAggregator aggregator,
        TestConfiguration config,
        boolean passed,
        BaselineData baseline,
        List<CovariateMisalignment> misalignments
    );
}
```

**Testing focus**:
- Verify all punit.* properties are published
- Verify budget info included at each scope level
- Verify transparent stats mode renders full explanation
- Verify expiration warnings shown at correct verbosity

**Complexity**: Medium
**Risk**: Low (pure formatting/output)

---

### Phase 7: Extract `TestConfiguration` to Own File

**Branch**: `refactor/extract-test-configuration`

**What it does**: Moves the `TestConfiguration` record to its own file for clarity.

**Current location**: Inner record at line 1595

**Changes**:
- Move to `TestConfiguration.java` in same package
- Keep package-private visibility
- No logic changes

**Testing focus**:
- Ensure all existing tests still pass (no behavior change)

**Complexity**: Low
**Risk**: Very Low

---

### Phase 8: Extract Invocation Context Classes

**Branch**: `refactor/extract-invocation-context`

**What it does**: Moves inner classes related to test invocation to their own files.

**Classes to extract**:
- `ProbabilisticTestInvocationContext` (line 1709)
- `TokenChargeRecorderParameterResolver` (line 1731)

**Changes**:
- Move each to its own file in same package
- Keep package-private visibility

**Testing focus**:
- Verify display name formatting
- Verify parameter resolution for TokenChargeRecorder

**Complexity**: Low
**Risk**: Very Low

---

## Execution Workflow

For each phase:

1. **Create feature branch**: `git checkout -b refactor/extract-XXX`
2. **Extract class**: Create new helper class with extracted logic
3. **Update extension**: Replace inline code with delegation to helper
4. **Add tests**: Create `XXXTest.java` with focused unit tests
5. **Run all tests**: `./gradlew test` to verify no regressions
6. **Commit**: Clear commit message describing extraction
7. **Push & PR**: `git push -u origin refactor/extract-XXX`
8. **Review & Merge**: After approval, merge to main

---

## Success Criteria

After all phases complete:

1. `ProbabilisticTestExtension` is ≤500 lines (down from 1744)
2. Each extracted class has ≥80% test coverage
3. All existing tests pass unchanged
4. Extension methods primarily delegate to helpers
5. No public API changes

---

## Rollback Strategy

Each phase is independently mergeable and revertable. If issues arise:
- Revert the specific phase's PR
- Fix issues on the feature branch
- Re-submit PR

The incremental approach ensures we never have a broken main branch.

---

## Estimated Effort

| Phase | Effort | Duration |
|-------|--------|----------|
| 1. FinalConfigurationLogger | Small | 30 min |
| 2. SampleFailureFormatter | Small | 30 min |
| 3. BudgetOrchestrator | Medium | 1-2 hours |
| 4. SampleExecutor | Medium | 1-2 hours |
| 5. BaselineSelectionOrchestrator | Large | 2-3 hours |
| 6. ResultPublisher | Medium | 1-2 hours |
| 7. TestConfiguration | Small | 15 min |
| 8. Invocation Context Classes | Small | 30 min |

**Total**: ~7-11 hours across phases

---

## Appendix: Dependency Graph

```
ProbabilisticTestExtension
├── FinalConfigurationLogger
│   └── PUnitReporter
├── SampleFailureFormatter  
├── BudgetOrchestrator
│   ├── CostBudgetMonitor
│   └── SharedBudgetMonitor
├── SampleExecutor
│   ├── SampleResultAggregator
│   ├── DefaultTokenChargeRecorder
│   └── PacingConfiguration
├── BaselineSelectionOrchestrator
│   ├── ConfigurationResolver
│   ├── BaselineRepository
│   ├── BaselineSelector
│   ├── CovariateProfileResolver
│   ├── FootprintComputer
│   ├── UseCaseCovariateExtractor
│   └── ProbabilisticTestValidator
├── ResultPublisher
│   ├── PUnitReporter
│   ├── StatisticalExplanationBuilder
│   ├── ConsoleExplanationRenderer
│   ├── ExpirationEvaluator
│   └── ExpirationWarningRenderer
├── TestConfiguration (standalone record)
└── ProbabilisticTestInvocationContext (standalone record)
```

