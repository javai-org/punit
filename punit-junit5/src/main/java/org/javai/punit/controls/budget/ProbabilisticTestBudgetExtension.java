package org.javai.punit.controls.budget;

import java.util.Optional;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that manages class-level budget for probabilistic tests.
 * 
 * <p>This extension is automatically applied when {@link ProbabilisticTestBudget}
 * is used on a test class. It:
 * <ul>
 *   <li>Creates a {@link SharedBudgetMonitor} for the class in BeforeAll</li>
 *   <li>Stores the monitor in the extension context store for method-level access</li>
 *   <li>Enables the {@link ProbabilisticTestExtension} to check class-level budgets</li>
 * </ul>
 * 
 * <h2>Extension Context Store</h2>
 * <p>The class budget monitor is stored with key {@code "classBudgetMonitor"} in the
 * class-level extension context store. The {@link ProbabilisticTestExtension} 
 * retrieves it during sample execution to check and update budgets.
 */
public class ProbabilisticTestBudgetExtension implements BeforeAllCallback {

    /**
     * Namespace for storing class-level budget data.
     */
    public static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ProbabilisticTestBudgetExtension.class);

    /**
     * Key for the class budget monitor in the extension context store.
     */
    public static final String CLASS_BUDGET_MONITOR_KEY = "classBudgetMonitor";

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        ProbabilisticTestBudget annotation = testClass.getAnnotation(ProbabilisticTestBudget.class);

        if (annotation == null) {
            return;
        }

        long timeBudgetMs = annotation.timeBudgetMs();
        long tokenBudget = annotation.tokenBudget();

        // Only create monitor if at least one budget is configured
        if (timeBudgetMs <= 0 && tokenBudget <= 0) {
            return;
        }

        SharedBudgetMonitor classBudgetMonitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS,
                timeBudgetMs,
                tokenBudget,
                annotation.onBudgetExhausted()
        );

        // Store in class-level context for access by ProbabilisticTestExtension
        context.getStore(NAMESPACE).put(CLASS_BUDGET_MONITOR_KEY, classBudgetMonitor);
    }

    /**
     * Retrieves the class budget monitor from the extension context, if present.
     *
     * @param context the extension context
     * @return an Optional containing the class budget monitor, or empty if not configured
     */
    public static Optional<SharedBudgetMonitor> getClassBudgetMonitor(ExtensionContext context) {
        // Walk up the context hierarchy to find the class-level context
        ExtensionContext current = context;
        while (current != null) {
            SharedBudgetMonitor monitor = current.getStore(NAMESPACE)
                    .get(CLASS_BUDGET_MONITOR_KEY, SharedBudgetMonitor.class);
            if (monitor != null) {
                return Optional.of(monitor);
            }
            current = current.getParent().orElse(null);
        }
        return Optional.empty();
    }
}

