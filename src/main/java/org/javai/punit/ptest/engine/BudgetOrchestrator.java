package org.javai.punit.ptest.engine;

import java.util.Optional;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.model.TerminationReason;

/**
 * Coordinates budget checking across suite, class, and method scopes.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Pre-sample budget checks (time and token budgets at all scopes)</li>
 *   <li>Post-sample budget checks (for dynamic token mode)</li>
 *   <li>Token recording and propagation across scopes</li>
 *   <li>Determining appropriate exhaustion behavior based on scope</li>
 *   <li>Building exhaustion messages for logging and error reporting</li>
 * </ul>
 *
 * <p>Budget checks follow precedence order: suite → class → method.
 * The first exhausted budget triggers termination.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class BudgetOrchestrator {

    /**
     * Result of a budget check operation.
     *
     * @param terminationReason the reason for termination, if budget is exhausted
     */
    record BudgetCheckResult(Optional<TerminationReason> terminationReason) {
        
        static BudgetCheckResult ok() {
            return new BudgetCheckResult(Optional.empty());
        }
        
        static BudgetCheckResult exhausted(TerminationReason reason) {
            return new BudgetCheckResult(Optional.of(reason));
        }
        
        boolean shouldTerminate() {
            return terminationReason.isPresent();
        }
    }

    /**
     * Checks all budget scopes before a sample (suite → class → method).
     *
     * <p>Checks are performed in precedence order:
     * <ol>
     *   <li>Suite-level time budget</li>
     *   <li>Suite-level token budget</li>
     *   <li>Class-level time budget</li>
     *   <li>Class-level token budget</li>
     *   <li>Method-level time budget</li>
     *   <li>Method-level token budget (pre-sample check)</li>
     * </ol>
     *
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @param classBudget the class-level budget monitor (may be null)
     * @param methodBudget the method-level budget monitor
     * @return the result of the budget check
     */
    BudgetCheckResult checkBeforeSample(
            SharedBudgetMonitor suiteBudget,
            SharedBudgetMonitor classBudget,
            CostBudgetMonitor methodBudget) {

        // 1. Suite-level budgets
        if (suiteBudget != null) {
            Optional<TerminationReason> reason = suiteBudget.checkTimeBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());

            reason = suiteBudget.checkTokenBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());
        }

        // 2. Class-level budgets
        if (classBudget != null) {
            Optional<TerminationReason> reason = classBudget.checkTimeBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());

            reason = classBudget.checkTokenBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());
        }

        // 3. Method-level budgets
        Optional<TerminationReason> reason = methodBudget.checkTimeBudget();
        if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());

        reason = methodBudget.checkTokenBudgetBeforeSample();
        if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());

        return BudgetCheckResult.ok();
    }

    /**
     * Checks all budget scopes after a sample (for dynamic token mode).
     *
     * <p>This is called after token consumption is recorded, primarily for
     * dynamic token budgets where consumption isn't known until after execution.
     *
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @param classBudget the class-level budget monitor (may be null)
     * @param methodBudget the method-level budget monitor
     * @return the result of the budget check
     */
    BudgetCheckResult checkAfterSample(
            SharedBudgetMonitor suiteBudget,
            SharedBudgetMonitor classBudget,
            CostBudgetMonitor methodBudget) {

        // Check in order: suite → class → method
        if (suiteBudget != null) {
            Optional<TerminationReason> reason = suiteBudget.checkTokenBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());
        }

        if (classBudget != null) {
            Optional<TerminationReason> reason = classBudget.checkTokenBudget();
            if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());
        }

        Optional<TerminationReason> reason = methodBudget.checkTokenBudgetAfterSample();
        if (reason.isPresent()) return BudgetCheckResult.exhausted(reason.get());

        return BudgetCheckResult.ok();
    }

    /**
     * Records tokens and propagates consumption to all active scopes.
     *
     * <p>Handles both static (pre-configured per-sample) and dynamic
     * (recorded during execution) token charging modes.
     *
     * @param tokenRecorder the dynamic token recorder (may be null)
     * @param methodBudget the method-level budget monitor
     * @param tokenMode the token charging mode
     * @param tokenCharge the static token charge per sample (if applicable)
     * @param classBudget the class-level budget monitor (may be null)
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @return the number of tokens consumed in this sample
     */
    long recordAndPropagateTokens(
            DefaultTokenChargeRecorder tokenRecorder,
            CostBudgetMonitor methodBudget,
            CostBudgetMonitor.TokenMode tokenMode,
            int tokenCharge,
            SharedBudgetMonitor classBudget,
            SharedBudgetMonitor suiteBudget) {

        long sampleTokens = 0;

        if (tokenRecorder != null) {
            sampleTokens = tokenRecorder.finalizeSample();
            methodBudget.recordDynamicTokens(sampleTokens);
        } else if (tokenMode == CostBudgetMonitor.TokenMode.STATIC) {
            sampleTokens = tokenCharge;
            methodBudget.recordStaticTokenCharge();
        }

        // Propagate to class and suite scopes
        if (sampleTokens > 0) {
            if (classBudget != null) {
                classBudget.addTokens(sampleTokens);
            }
            if (suiteBudget != null) {
                suiteBudget.addTokens(sampleTokens);
            }
        }

        return sampleTokens;
    }

    /**
     * Determines the budget exhaustion behavior based on the scope that triggered it.
     *
     * <p>Each scope can have its own configured behavior:
     * <ul>
     *   <li>Suite-level exhaustion → uses suite's configured behavior</li>
     *   <li>Class-level exhaustion → uses class's configured behavior</li>
     *   <li>Method-level exhaustion → uses method's configured behavior</li>
     * </ul>
     *
     * @param reason the termination reason
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @param classBudget the class-level budget monitor (may be null)
     * @param methodBehavior the method-level exhaustion behavior
     * @return the appropriate exhaustion behavior
     */
    BudgetExhaustedBehavior determineBehavior(
            TerminationReason reason,
            SharedBudgetMonitor suiteBudget,
            SharedBudgetMonitor classBudget,
            BudgetExhaustedBehavior methodBehavior) {

        if (reason.name().startsWith("SUITE_") && suiteBudget != null) {
            return suiteBudget.getOnBudgetExhausted();
        } else if (reason.name().startsWith("CLASS_") && classBudget != null) {
            return classBudget.getOnBudgetExhausted();
        } else {
            return methodBehavior;
        }
    }

    /**
     * Builds a detailed message describing the budget exhaustion.
     *
     * <p>The message includes the scope, budget value, and consumed amount.
     *
     * @param reason the termination reason
     * @param methodBudget the method-level budget monitor
     * @param classBudget the class-level budget monitor (may be null)
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @return the formatted exhaustion message
     */
    String buildExhaustionMessage(
            TerminationReason reason,
            CostBudgetMonitor methodBudget,
            SharedBudgetMonitor classBudget,
            SharedBudgetMonitor suiteBudget) {

        switch (reason) {
            case SUITE_TIME_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite time budget exhausted: %dms elapsed >= %dms budget",
                                suiteBudget.getElapsedMs(), suiteBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case SUITE_TOKEN_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite token budget exhausted: %d tokens >= %d budget",
                                suiteBudget.getTokensConsumed(), suiteBudget.getTokenBudget())
                        : reason.getDescription();
            case CLASS_TIME_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class time budget exhausted: %dms elapsed >= %dms budget",
                                classBudget.getElapsedMs(), classBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case CLASS_TOKEN_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class token budget exhausted: %d tokens >= %d budget",
                                classBudget.getTokensConsumed(), classBudget.getTokenBudget())
                        : reason.getDescription();
            case METHOD_TIME_BUDGET_EXHAUSTED:
                return String.format("Method time budget exhausted: %dms elapsed >= %dms budget",
                        methodBudget.getElapsedMs(), methodBudget.getTimeBudgetMs());
            case METHOD_TOKEN_BUDGET_EXHAUSTED:
                return String.format("Method token budget exhausted: %d tokens >= %d budget",
                        methodBudget.getTokensConsumed(), methodBudget.getTokenBudget());
            default:
                return reason.getDescription();
        }
    }

    /**
     * Builds a failure message for budget exhaustion scenarios.
     *
     * <p>This is used when the test is forced to fail due to budget limits,
     * not pass rate. The message includes execution statistics at termination.
     *
     * @param terminationReason the termination reason
     * @param terminationDetails the detailed termination message
     * @param samplesExecuted number of samples executed before termination
     * @param plannedSamples total number of planned samples
     * @param observedPassRate the pass rate at termination
     * @param successes number of successful samples
     * @param minPassRate the required pass rate
     * @param elapsedMs elapsed time in milliseconds
     * @return the formatted failure message
     */
    String buildExhaustionFailureMessage(
            TerminationReason terminationReason,
            String terminationDetails,
            int samplesExecuted,
            int plannedSamples,
            double observedPassRate,
            int successes,
            double minPassRate,
            long elapsedMs) {

        StringBuilder sb = new StringBuilder();

        String failureReason = terminationReason != null 
                ? terminationReason.getDescription() 
                : "Budget exhausted";
        sb.append(String.format("PUnit FAILED: %s.%n", failureReason));

        if (terminationDetails != null && !terminationDetails.isEmpty()) {
            sb.append(String.format("  %s%n", terminationDetails));
        }

        sb.append(String.format("%n  Samples executed: %d of %d%n",
                samplesExecuted, plannedSamples));
        sb.append(String.format("  Pass rate at termination: %.1f%% (%d/%d)%n",
                observedPassRate * 100.0,
                successes,
                samplesExecuted));
        sb.append(String.format("  Required pass rate: %.1f%%%n", minPassRate * 100.0));
        sb.append(String.format("  Elapsed: %dms%n", elapsedMs));

        return sb.toString();
    }
}

