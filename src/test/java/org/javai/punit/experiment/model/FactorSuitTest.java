package org.javai.punit.experiment.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FactorSuit}.
 */
class FactorSuitTest {

    @Test
    void shouldCreateFromKeyValuePairs() {
        FactorSuit suit = FactorSuit.of(
                "model", "gpt-4",
                "temperature", 0.7,
                "maxTokens", 1000
        );

        assertEquals("gpt-4", suit.get("model"));
        assertEquals(0.7, suit.<Double>get("temperature"));
        assertEquals(1000, suit.<Integer>get("maxTokens"));
        assertEquals(3, suit.size());
    }

    @Test
    void shouldCreateFromMap() {
        Map<String, Object> values = Map.of(
                "model", "gpt-4",
                "temperature", 0.7
        );

        FactorSuit suit = FactorSuit.of(values);

        assertEquals("gpt-4", suit.get("model"));
        assertEquals(0.7, suit.<Double>get("temperature"));
    }

    @Test
    void shouldBeImmutable() {
        FactorSuit suit = FactorSuit.of("key", "value");

        assertThrows(UnsupportedOperationException.class, () ->
                suit.asMap().put("newKey", "newValue")
        );
    }

    @Test
    void shouldCreateModifiedCopyWithWith() {
        FactorSuit original = FactorSuit.of("model", "gpt-4", "temperature", 0.7);

        FactorSuit modified = original.with("temperature", 0.9);

        // Original unchanged
        assertEquals(0.7, original.<Double>get("temperature"));
        // Modified has new value
        assertEquals(0.9, modified.<Double>get("temperature"));
        // Other values preserved
        assertEquals("gpt-4", modified.get("model"));
    }

    @Test
    void shouldAddNewFactorWithWith() {
        FactorSuit original = FactorSuit.of("model", "gpt-4");

        FactorSuit modified = original.with("temperature", 0.7);

        assertEquals(1, original.size());
        assertEquals(2, modified.size());
        assertEquals(0.7, modified.<Double>get("temperature"));
    }

    @Test
    void shouldReturnNullForMissingKey() {
        FactorSuit suit = FactorSuit.of("model", "gpt-4");

        assertNull(suit.get("nonexistent"));
    }

    @Test
    void shouldCheckContains() {
        FactorSuit suit = FactorSuit.of("model", "gpt-4");

        assertTrue(suit.contains("model"));
        assertFalse(suit.contains("temperature"));
    }

    @Test
    void shouldReturnFactorNames() {
        FactorSuit suit = FactorSuit.of("model", "gpt-4", "temperature", 0.7);

        assertTrue(suit.factorNames().contains("model"));
        assertTrue(suit.factorNames().contains("temperature"));
        assertEquals(2, suit.factorNames().size());
    }

    @Test
    void shouldCreateEmptySuit() {
        FactorSuit suit = FactorSuit.empty();

        assertEquals(0, suit.size());
        assertTrue(suit.factorNames().isEmpty());
    }

    @Test
    void shouldRejectOddNumberOfArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                FactorSuit.of("key1", "value1", "key2")
        );
    }

    @Test
    void shouldRejectNonStringKeys() {
        assertThrows(IllegalArgumentException.class, () ->
                FactorSuit.of(123, "value")
        );
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        FactorSuit suit1 = FactorSuit.of("model", "gpt-4", "temperature", 0.7);
        FactorSuit suit2 = FactorSuit.of("model", "gpt-4", "temperature", 0.7);
        FactorSuit suit3 = FactorSuit.of("model", "gpt-3.5");

        assertEquals(suit1, suit2);
        assertEquals(suit1.hashCode(), suit2.hashCode());
        assertNotEquals(suit1, suit3);
    }

    @Test
    void shouldHaveReadableToString() {
        FactorSuit suit = FactorSuit.of("model", "gpt-4");

        String str = suit.toString();
        assertTrue(str.contains("model"));
        assertTrue(str.contains("gpt-4"));
    }
}
