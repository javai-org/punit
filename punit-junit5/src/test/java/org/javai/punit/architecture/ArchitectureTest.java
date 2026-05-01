package org.javai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests to enforce PUnit's structural constraints.
 *
 * <p>The original three-pillar layout
 * ({@code experiment/} / {@code ptest/} / {@code spec/}) is gone — the
 * legacy {@code ptest} and {@code experiment} packages were retired in
 * Stage 8 (DIR-PUNIT-S8), leaving the typed pipeline as the sole
 * authoring surface. The pillar-independence rules that policed those
 * packages have retired alongside them.
 *
 * <p>The remaining rules enforce abstraction-level discipline that
 * still applies to surviving classes — evaluators / resolvers /
 * deciders may not depend on reporting; renderers may not depend on
 * statistical computation; strategies may not instantiate
 * {@link org.javai.punit.reporting.PUnitReporter} directly.
 */
@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit");
    }

    @Nested
    @DisplayName("Abstraction Level Enforcement")
    class AbstractionLevelEnforcement {

        /**
         * Evaluators, resolvers, and deciders are computation / decision
         * classes. They must not depend on reporting infrastructure
         * (formatting, rendering). Formatting belongs in dedicated
         * formatter / renderer / messages classes.
         */
        @Test
        @DisplayName("Evaluators, resolvers, and deciders must not depend on reporting")
        void evaluatorsResolversDecidersMustNotDependOnReporting() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Evaluator")
                    .or().haveSimpleNameEndingWith("Resolver")
                    .or().haveSimpleNameEndingWith("Decider")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..reporting..")
                    .because("evaluators/resolvers/deciders compute decisions; "
                            + "formatting belongs in dedicated formatter/renderer classes");

            rule.check(classes);
        }

        /**
         * Renderers format pre-computed data for display. They must not
         * perform statistical computation themselves — that belongs in
         * estimators and derivers.
         */
        @Test
        @DisplayName("Renderers must not depend on statistical computation classes")
        void renderersMustNotDependOnStatisticalComputation() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Renderer")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Estimator")
                    .orShould().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Deriver")
                    .because("renderers format pre-computed data; "
                            + "statistical computation belongs in estimator/deriver classes");

            rule.check(classes);
        }
    }
}
