package org.javai.punit.experiment.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OptimizationObjective}.
 */
class OptimizationObjectiveTest {

    @Test
    void maximizeShouldPreferHigherScores() {
        assertTrue(OptimizationObjective.MAXIMIZE.isBetter(0.9, 0.8));
        assertFalse(OptimizationObjective.MAXIMIZE.isBetter(0.8, 0.9));
        assertFalse(OptimizationObjective.MAXIMIZE.isBetter(0.8, 0.8));
    }

    @Test
    void minimizeShouldPreferLowerScores() {
        assertTrue(OptimizationObjective.MINIMIZE.isBetter(0.8, 0.9));
        assertFalse(OptimizationObjective.MINIMIZE.isBetter(0.9, 0.8));
        assertFalse(OptimizationObjective.MINIMIZE.isBetter(0.8, 0.8));
    }

    @Test
    void shouldHandleNegativeScores() {
        assertTrue(OptimizationObjective.MAXIMIZE.isBetter(-0.5, -0.9));
        assertTrue(OptimizationObjective.MINIMIZE.isBetter(-0.9, -0.5));
    }

    @Test
    void shouldHandleZero() {
        assertTrue(OptimizationObjective.MAXIMIZE.isBetter(0.1, 0.0));
        assertTrue(OptimizationObjective.MINIMIZE.isBetter(0.0, 0.1));
    }
}
