package org.javai.punit.junit5;

import java.nio.file.Path;

import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.engine.baseline.BaselineResolver;
import org.javai.punit.engine.baseline.YamlBaselineProvider;

/**
 * Resolves the {@link BaselineProvider} consumed by
 * {@link Punit#testing}-style empirical criteria and emitted to by
 * {@link Punit#measuring}-style experiments. Directory resolution
 * delegates to {@link BaselineResolver#defaultDir()} — the same
 * implementation any other consumer (e.g.
 * {@link org.javai.punit.power.PowerAnalysis}) calls — so framework
 * and user code resolve the same path through one source of truth.
 *
 * <p>Precedence (defined in {@code BaselineResolver}):
 *
 * <ol>
 *   <li>System property {@value BaselineResolver#BASELINE_DIR_PROPERTY}
 *       — explicit per-run override (CI environments, ad-hoc
 *       invocations).</li>
 *   <li>Project convention {@value BaselineResolver#CONVENTION_PATH}
 *       — the default; baselines travel with source.</li>
 * </ol>
 *
 * <p>A future revision can layer a JUnit configuration-parameter
 * lookup in between when {@link Punit} gains an
 * {@code ExtensionContext}-aware overload. Not needed for 1.0.
 *
 * <p>When no candidate resolves to an existing directory, the
 * provider's lookups return empty and empirical criteria yield
 * {@code INCONCLUSIVE} per the resolver contract. Measure
 * experiments still run; their baseline write is silently skipped
 * (see {@link Punit.MeasureBuilder#run}).
 */
final class BaselineProviderResolver {

    /**
     * Re-exposed for tests that want to set the system property
     * deterministically; the canonical declaration is on
     * {@link BaselineResolver}.
     */
    static final String BASELINE_DIR_PROPERTY =
            BaselineResolver.BASELINE_DIR_PROPERTY;

    private BaselineProviderResolver() { }

    /**
     * @return the configured baseline directory. The directory is
     *         not required to exist — {@link BaselineEmitter}
     *         creates it on first write, and the
     *         {@link BaselineResolver} downstream handles missing
     *         directories (returns empty for any lookup).
     */
    static Path resolveDir() {
        return BaselineResolver.defaultDir();
    }

    /**
     * @return a {@link YamlBaselineProvider} backed by the resolved
     *         baseline directory. When the directory is missing the
     *         provider's lookups return empty for every query, so
     *         the empirical-criterion path produces
     *         {@code INCONCLUSIVE} — the same outcome as a hard
     *         {@link BaselineProvider#EMPTY}.
     */
    static BaselineProvider resolve() {
        return new YamlBaselineProvider(resolveDir());
    }
}
