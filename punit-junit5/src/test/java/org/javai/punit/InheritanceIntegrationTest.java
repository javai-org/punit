package org.javai.punit;

import org.javai.punit.testsubjects.InheritanceTestSubjects.InheritedMeasureExperiment;
import org.javai.punit.testsubjects.InheritanceTestSubjects.InheritedProbabilisticTest;
import org.javai.punit.testsubjects.InheritanceTestSubjects.InheritedProbabilisticTestWithInputs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests verifying the DD-06 inheritance model.
 *
 * <p>These tests validate that a JUnit test class can inherit from a reliability
 * specification superclass and have all PUnit annotations, {@code UseCaseFactory}
 * fields, and {@code @InputSource} methods discovered and executed correctly.
 */
@DisplayName("DD-06 Inheritance Model")
class InheritanceIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Nested
    @DisplayName("@ProbabilisticTest inheritance")
    class ProbabilisticTestInheritance {

        @Test
        @DisplayName("subclass inherits @ProbabilisticTest and UseCaseFactory from superclass")
        void subclassInheritsProbabilisticTest() {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(InheritedProbabilisticTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(5)
                            .succeeded(5)
                            .failed(0));
        }

        @Test
        @DisplayName("subclass inherits @ProbabilisticTest with @InputSource from superclass")
        void subclassInheritsProbabilisticTestWithInputs() {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(
                            InheritedProbabilisticTestWithInputs.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(6)
                            .succeeded(6)
                            .failed(0));
        }
    }

    @Nested
    @DisplayName("@MeasureExperiment inheritance")
    class MeasureExperimentInheritance {

        @Test
        @DisplayName("subclass inherits @MeasureExperiment and UseCaseFactory from superclass")
        void subclassInheritsMeasureExperiment() {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(InheritedMeasureExperiment.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(3)
                            .succeeded(3)
                            .failed(0));
        }
    }
}
