package org.javai.punit.junit5;

import org.javai.punit.api.typed.spec.BaselineProvider;

/**
 * Resolves a {@link BaselineProvider} for the current test run.
 *
 * <p>Stage 5 Slice B ships a placeholder that always returns
 * {@link BaselineProvider#EMPTY}. Empirical criteria therefore yield
 * {@code INCONCLUSIVE} until Slice 5D wires the real precedence:
 *
 * <ol>
 *   <li>System property {@code punit.baseline.dir}.</li>
 *   <li>JUnit configuration parameter {@code punit.baseline.dir}.</li>
 *   <li>Project convention directory
 *       {@code src/test/resources/punit/baselines/}.</li>
 * </ol>
 *
 * <p>Static (no JUnit-context parameter) because {@link Punit#run} is
 * a plain static helper called from a normal {@code void} test
 * method — there is no {@code ExtensionContext} on hand. Slice 5D
 * may extend with a context-aware overload if needed.
 */
final class BaselineProviderResolver {

    private BaselineProviderResolver() { }

    static BaselineProvider resolve() {
        return BaselineProvider.EMPTY;
    }
}
