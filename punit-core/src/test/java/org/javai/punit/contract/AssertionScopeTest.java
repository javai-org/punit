package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AssertionScope")
class AssertionScopeTest {

    @AfterEach
    void cleanup() {
        AssertionScope.end();
    }

    @Test
    @DisplayName("current() returns null when no scope active")
    void currentReturnsNullWhenNoScope() {
        assertThat(AssertionScope.current()).isNull();
    }

    @Test
    @DisplayName("begin() creates a new scope")
    void beginCreatesScope() {
        AssertionScope.begin();
        assertThat(AssertionScope.current()).isNotNull();
    }

    @Test
    @DisplayName("end() removes the scope")
    void endRemovesScope() {
        AssertionScope.begin();
        AssertionScope.end();
        assertThat(AssertionScope.current()).isNull();
    }

    @Nested
    @DisplayName("dimension tracking")
    class DimensionTracking {

        @Test
        @DisplayName("initially no dimensions asserted")
        void initiallyNoDimensions() {
            AssertionScope.begin();
            AssertionScope scope = AssertionScope.current();

            assertThat(scope.isFunctionalAsserted()).isFalse();
            assertThat(scope.isLatencyAsserted()).isFalse();
        }

        @Test
        @DisplayName("recordFunctional sets functional asserted")
        void recordFunctionalSetsAsserted() {
            AssertionScope.begin();
            AssertionScope scope = AssertionScope.current();

            scope.recordFunctional(true);

            assertThat(scope.isFunctionalAsserted()).isTrue();
            assertThat(scope.isFunctionalPassed()).isTrue();
            assertThat(scope.isLatencyAsserted()).isFalse();
        }

        @Test
        @DisplayName("recordLatency sets latency asserted")
        void recordLatencySetsAsserted() {
            AssertionScope.begin();
            AssertionScope scope = AssertionScope.current();

            scope.recordLatency(false);

            assertThat(scope.isLatencyAsserted()).isTrue();
            assertThat(scope.isLatencyPassed()).isFalse();
            assertThat(scope.isFunctionalAsserted()).isFalse();
        }

        @Test
        @DisplayName("both dimensions can be recorded independently")
        void bothDimensionsRecorded() {
            AssertionScope.begin();
            AssertionScope scope = AssertionScope.current();

            scope.recordFunctional(true);
            scope.recordLatency(false);

            assertThat(scope.isFunctionalAsserted()).isTrue();
            assertThat(scope.isFunctionalPassed()).isTrue();
            assertThat(scope.isLatencyAsserted()).isTrue();
            assertThat(scope.isLatencyPassed()).isFalse();
        }
    }
}
