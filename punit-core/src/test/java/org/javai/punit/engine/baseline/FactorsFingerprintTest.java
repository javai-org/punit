package org.javai.punit.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactorsFingerprint")
class FactorsFingerprintTest {

    record Factors(String model, double temperature) { }

    @Test
    @DisplayName("8-char hex for any non-null value")
    void hexLengthForNonNull() {
        String fingerprint = FactorsFingerprint.of(new Factors("gpt-4o", 0.0));

        assertThat(fingerprint).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("deterministic — equal inputs produce equal fingerprints")
    void deterministic() {
        String a = FactorsFingerprint.of(new Factors("gpt-4o", 0.0));
        String b = FactorsFingerprint.of(new Factors("gpt-4o", 0.0));

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("differs when any factor field differs")
    void contentSensitivity() {
        String a = FactorsFingerprint.of(new Factors("gpt-4o", 0.0));
        String b = FactorsFingerprint.of(new Factors("gpt-4o", 0.1));
        String c = FactorsFingerprint.of(new Factors("gpt-3.5", 0.0));

        assertThat(a).isNotEqualTo(b).isNotEqualTo(c);
        assertThat(b).isNotEqualTo(c);
    }

    @Test
    @DisplayName("null factors produce the literal 'null' fingerprint")
    void nullFactors() {
        assertThat(FactorsFingerprint.of(null)).isEqualTo("null");
    }

    @Test
    @DisplayName("'null' literal cannot collide with any hex fingerprint")
    void nullSentinelIsDistinct() {
        String literal = FactorsFingerprint.of(null);
        String hex = FactorsFingerprint.of(new Factors("anything", 1.0));

        assertThat(literal).isNotEqualTo(hex);
        assertThat(hex).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("works for non-record types — relies on String.valueOf, so toString stability is the caller's contract")
    void stringValueOfUsage() {
        String forString = FactorsFingerprint.of("plain string");
        String forInteger = FactorsFingerprint.of(42);

        assertThat(forString).hasSize(8);
        assertThat(forInteger).hasSize(8);
        assertThat(forString).isNotEqualTo(forInteger);
    }
}
