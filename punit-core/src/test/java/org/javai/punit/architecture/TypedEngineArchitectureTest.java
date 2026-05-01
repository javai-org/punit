package org.javai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for the typed-model engine under
 * {@code org.javai.punit.engine}.
 *
 * <p>The core guarantee this stage locks in: the engine does not
 * branch on spec subtype. It reaches flavour-specific behaviour only
 * through the strategy methods on
 * {@code org.javai.punit.api.spec.Spec}.
 */
@DisplayName("typed engine architecture rules")
class TypedEngineArchitectureTest {

    private static JavaClasses engineClasses;

    @BeforeAll
    static void importClasses() {
        engineClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit.engine");
    }

    @Test
    @DisplayName("engine dispatcher must not import concrete Spec subtypes")
    void engineMustNotImportConcreteSpecs() {
        // Scope is the dispatcher itself — classes directly in
        // org.javai.punit.engine (Engine.java, BudgetTracker.java,
        // SerialSampleExecutor.java, …) — not the subpackages that
        // host genuinely Experiment-aware machinery (engine.criteria
        // holds BernoulliPassRate, which references Experiment via
        // its empiricalFrom(Supplier<Experiment>) pinning API; that's
        // value-typed reference, not subtype-discrimination).
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.engine")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "org.javai.punit.api.spec.Experiment")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "org.javai.punit.api.spec.ProbabilisticTest")
                .because("engine must dispatch through the Spec strategy interface, "
                        + "never instanceof / switch on subtype");
        rule.check(engineClasses);
    }

    @Test
    @DisplayName("engine must not depend on JUnit")
    void engineMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.engine..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..");
        rule.check(engineClasses);
    }
}
