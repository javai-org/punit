package org.javai.punit.ptest.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.ProbabilisticTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

@DisplayName("Concurrent execution")
class ConcurrentExecutionTest {

    @Test
    @DisplayName("basic probabilistic test passes through concurrent infrastructure")
    void basicTest() {
        EngineExecutionResults results = EngineTestKit
                .engine("junit-jupiter")
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectMethod(BasicTestSubject.class, "alwaysPass"))
                .execute();

        results.testEvents().assertThatEvents().haveExactly(1,
                org.junit.platform.testkit.engine.EventConditions.event(
                        org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully()));
    }

    public static class BasicTestSubject {
        @ProbabilisticTest(samples = 5, minPassRate = 1.0)
        void alwaysPass() {
            assertThat(true).isTrue();
        }
    }
}
