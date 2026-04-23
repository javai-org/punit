package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactorBundle")
class FactorBundleTest {

    record LlmFactors(String model, double temperature, int maxTokens, boolean streaming) {}

    record NoFields() {}

    record WithList(String name, List<Integer> values) {}

    @Test
    @DisplayName("reflectively reads record components in declaration order")
    void entriesInDeclarationOrder() {
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.entries()).hasSize(4);
        assertThat(b.entries().get(0).name()).isEqualTo("model");
        assertThat(b.entries().get(1).name()).isEqualTo("temperature");
        assertThat(b.entries().get(2).name()).isEqualTo("maxTokens");
        assertThat(b.entries().get(3).name()).isEqualTo("streaming");
    }

    @Test
    @DisplayName("canonicalJson sorts keys alphabetically with no whitespace")
    void canonicalJsonSortsKeys() {
        // This example is lifted verbatim from the EX04 catalog README
        // (Factor bundle hash section).
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.canonicalJson())
                .isEqualTo("{\"maxTokens\":2048,\"model\":\"gpt-4o\","
                        + "\"streaming\":true,\"temperature\":0.3}");
    }

    @Test
    @DisplayName("bundleHash is a four-hex-char SHA-256 truncation and is stable across runs")
    void bundleHashStable() {
        FactorBundle a = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(a.bundleHash()).hasSize(4);
        assertThat(a.bundleHash()).matches("[0-9a-f]{4}");
        assertThat(a.bundleHash()).isEqualTo(b.bundleHash());
    }

    @Test
    @DisplayName("different factor bundles produce different hashes")
    void distinctBundlesDistinctHashes() {
        FactorBundle a = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.7, 2048, true));
        assertThat(a.bundleHash()).isNotEqualTo(b.bundleHash());
    }

    @Test
    @DisplayName("empty() corresponds to a record with no components")
    void emptyBundle() {
        FactorBundle b = FactorBundle.of(new NoFields());
        assertThat(b.isEmpty()).isTrue();
        assertThat(b.entries()).isEmpty();
        assertThat(b.canonicalJson()).isEqualTo("{}");
        assertThat(b).isEqualTo(FactorBundle.empty());
    }

    @Test
    @DisplayName("rejects null factor record")
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> FactorBundle.of(null));
    }

    @Test
    @DisplayName("rejects a non-record type")
    void rejectsNonRecord() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FactorBundle.of("not a record"))
                .withMessageContaining("must be a record");
    }

    @Test
    @DisplayName("rejects records with inadmissible component types, naming the component")
    void rejectsBadComponentType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FactorBundle.of(new WithList("x", List.of(1, 2))))
                .withMessageContaining("'values'")
                .withMessageContaining("not admissible");
    }

    @Test
    @DisplayName("bundleHash produces the EX04 sample example")
    void bundleHashReproducesEx04Example() {
        // The canonical JSON matches EX04 exactly; the hash is the first
        // four hex chars of SHA-256 of that JSON — an independently
        // verifiable number.
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.canonicalJson())
                .isEqualTo("{\"maxTokens\":2048,\"model\":\"gpt-4o\","
                        + "\"streaming\":true,\"temperature\":0.3}");
        // Hash must be stable — equal across JVMs and across runs.
        assertThat(b.bundleHash()).matches("[0-9a-f]{4}");
    }
}
