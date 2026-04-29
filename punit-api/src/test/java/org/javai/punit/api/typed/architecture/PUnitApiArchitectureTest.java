package org.javai.punit.api.typed.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for the {@code punit-api} module.
 *
 * <p>{@code punit-api} is the thin public authoring surface. It must
 * not drag in any framework internals and must not depend on JUnit —
 * authors and downstream consumers must be able to use these types
 * without JUnit on the classpath.
 */
@DisplayName("punit-api Architecture Rules")
class PUnitApiArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit.api.typed");
    }

    @Test
    @DisplayName("punit-api classes must not depend on JUnit")
    void mustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("punit-api is a JUnit-free author-facing surface");
        rule.check(classes);
    }

    @Test
    @DisplayName("punit-api classes must not depend on any other punit package")
    void mustNotDependOnInternalPUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.javai.punit.ptest..",
                        "org.javai.punit.experiment..",
                        "org.javai.punit.statistics..",
                        "org.javai.punit.spec..",
                        "org.javai.punit.model..",
                        "org.javai.punit.controls..",
                        "org.javai.punit.reporting..",
                        "org.javai.punit.verdict..",
                        "org.javai.punit.contract..",
                        "org.javai.punit.usecase..",
                        "org.javai.punit.util.."
                )
                .because("the typed API points outward, not to framework internals");
        rule.check(classes);
    }

    @Test
    @DisplayName("punit-api classes must not depend on Apache Commons (statistics stays out of the API)")
    void mustNotDependOnApacheCommons() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.apache.commons..")
                .because("commons-statistics belongs in punit-core; keeping it off the author-facing classpath "
                        + "stops a transitive dependency leaking to downstream consumers");
        rule.check(classes);
    }

    @Test
    @DisplayName("retired Stage-3 type names must not be re-introduced under typed..")
    void mustNotReintroduceRetiredTypes() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().haveSimpleNameStartingWith("VerdictDimension")
                .orShould().haveSimpleNameStartingWith("LatencyVerdict")
                .orShould().haveSimpleNameStartingWith("BernoulliFunctionalVerdict")
                .because("the Stage-3 two-dimensional verdict apparatus retired in Stage 3.5; "
                        + "criterion roles + Verdict.compose replace it, and the names should not return");
        rule.check(classes);
    }

    @Test
    @DisplayName("the four specs only expose verb-form static factories — no builder() entry remains")
    void specsExposeOnlyVerbFormFactories() {
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.javai.punit.api.typed.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.javai.punit.api.typed.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.javai.punit.api.typed.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.javai.punit.api.typed.spec.ProbabilisticTest")
                .should().haveName("builder")
                .because("the compositional refactor replaces .builder() with the verb-form factories "
                        + "measuring / exploring / optimizing / testing — re-introducing builder() "
                        + "splits the entry surface and dilutes the production/consumption seam");
    rule.check(classes);
    }

    @Test
    @DisplayName("each spec exposes its verb-form factory")
    void specsHaveVerbFormFactory() {
        verbFormFactory("Experiment", "measuring");
        verbFormFactory("Experiment", "exploring");
        verbFormFactory("Experiment", "optimizing");
        verbFormFactory("ProbabilisticTest", "testing");
    }

    private static void verbFormFactory(String specSimpleName, String verb) {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.javai.punit.api.typed.spec." + specSimpleName)
                .and().haveName(verb)
                .should().beStatic()
                .andShould().bePublic()
                .because(specSimpleName + " is composed via " + verb + "(...) — see DG01/DG02 for shape, "
                        + "EX01/EX02/EX03/CR04 for spec-kind specifics");
        rule.check(classes);
    }
}
