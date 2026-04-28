package org.javai.punit.junit5;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.engine.baseline.BaselineResolver;
import org.javai.punit.engine.baseline.YamlBaselineProvider;

/**
 * Resolves the baseline directory and the
 * {@link BaselineProvider} consumed by {@link Punit#testing}-style
 * empirical criteria and emitted to by {@link Punit#measuring}-style
 * experiments.
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>System property {@value #BASELINE_DIR_PROPERTY} — explicit
 *       per-run override (CI environments, ad-hoc invocations).</li>
 *   <li>Project convention {@value #CONVENTION_PATH} — the default;
 *       baselines travel with source.</li>
 * </ol>
 *
 * <p>A future revision can layer a JUnit configuration-parameter
 * lookup in between when {@link Punit} gains an
 * {@code ExtensionContext}-aware overload. Not needed for 1.0.
 *
 * <p>When no candidate resolves to an existing directory, the
 * provider is {@link BaselineProvider#EMPTY} and empirical criteria
 * yield {@code INCONCLUSIVE} per the resolver contract. Measure
 * experiments still run; their baseline write is silently skipped
 * (see {@link Punit.MeasureBuilder#run}).
 */
final class BaselineProviderResolver {

    /** System property used to override the baseline directory. */
    static final String BASELINE_DIR_PROPERTY = "punit.baseline.dir";

    /** Convention-default directory under the project root. */
    static final String CONVENTION_PATH = "src/test/resources/punit/baselines";

    private BaselineProviderResolver() { }

    /**
     * @return the configured baseline directory. Returns the
     *         system-property path when set, otherwise the
     *         convention-default {@value #CONVENTION_PATH} path.
     *         The directory is not required to exist —
     *         {@link BaselineEmitter} creates it on first write,
     *         and the {@link BaselineResolver} downstream handles
     *         missing directories (returns empty for any lookup).
     *         Symmetric with the legacy convention.
     */
    static Path resolveDir() {
        return systemProperty().orElseGet(() -> Paths.get(CONVENTION_PATH));
    }

    /**
     * @return a {@link YamlBaselineProvider} backed by the resolved
     *         baseline directory. When the directory is missing the
     *         provider's lookups return empty for every query (the
     *         underlying {@link BaselineResolver} treats a missing
     *         directory as "no baselines available"), so the
     *         empirical-criterion path produces {@code INCONCLUSIVE}
     *         — the same outcome as a hard {@link BaselineProvider#EMPTY}.
     */
    static BaselineProvider resolve() {
        return new YamlBaselineProvider(resolveDir());
    }

    private static Optional<Path> systemProperty() {
        String value = System.getProperty(BASELINE_DIR_PROPERTY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(value.trim()));
    }
}
