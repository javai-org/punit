package org.javai.punit.junit5;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.javai.punit.api.typed.spec.BaselineProvider;
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
     * @return the configured baseline directory, when one is
     *         specified and exists; empty otherwise
     */
    static Optional<Path> resolveDir() {
        Optional<Path> fromProperty = systemProperty();
        if (fromProperty.isPresent()) {
            return fromProperty.filter(Files::isDirectory);
        }
        Path convention = Paths.get(CONVENTION_PATH);
        return Files.isDirectory(convention) ? Optional.of(convention) : Optional.empty();
    }

    /**
     * @return a {@link YamlBaselineProvider} when a baseline
     *         directory resolves; {@link BaselineProvider#EMPTY}
     *         otherwise
     */
    static BaselineProvider resolve() {
        return resolveDir()
                .<BaselineProvider>map(YamlBaselineProvider::new)
                .orElse(BaselineProvider.EMPTY);
    }

    private static Optional<Path> systemProperty() {
        String value = System.getProperty(BASELINE_DIR_PROPERTY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(value.trim()));
    }
}
