package org.javai.punit.experiment.engine.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DiffAnchorGenerator")
class DiffAnchorGeneratorTest {

    @Nested
    @DisplayName("anchorFor()")
    class AnchorFor {

        @Test
        @DisplayName("returns same value on repeated calls")
        void returnsSameValueOnRepeatedCalls() {
            String first = DiffAnchorGenerator.anchorFor(0);
            String second = DiffAnchorGenerator.anchorFor(0);

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("different indices produce different anchors")
        void differentIndicesProduceDifferentAnchors() {
            String anchor0 = DiffAnchorGenerator.anchorFor(0);
            String anchor1 = DiffAnchorGenerator.anchorFor(1);

            assertThat(anchor0).isNotEqualTo(anchor1);
        }

        @Test
        @DisplayName("returns exactly 8 lowercase hex characters")
        void returnsExactly8LowercaseHexCharacters() {
            for (int i = 0; i < 10; i++) {
                assertThat(DiffAnchorGenerator.anchorFor(i)).matches("[0-9a-f]{8}");
            }
        }

        @Test
        @DisplayName("produces known values for fixed seed")
        void producesKnownValuesForFixedSeed() {
            assertThat(DiffAnchorGenerator.anchorFor(0)).isEqualTo("0dfe8af7");
            assertThat(DiffAnchorGenerator.anchorFor(1)).isEqualTo("0c45c028");
            assertThat(DiffAnchorGenerator.anchorFor(2)).isEqualTo("f12bbb4b");
            assertThat(DiffAnchorGenerator.anchorFor(3)).isEqualTo("b52c856d");
            assertThat(DiffAnchorGenerator.anchorFor(4)).isEqualTo("17610c9a");
        }

        @Test
        @DisplayName("rejects negative index")
        void rejectsNegativeIndex() {
            assertThatThrownBy(() -> DiffAnchorGenerator.anchorFor(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleIndex must be non-negative");
        }
    }

    @Nested
    @DisplayName("anchorLine()")
    class AnchorLine {

        @Test
        @DisplayName("formats anchor line with sample index and hex value")
        void formatsAnchorLineWithSampleIndexAndHexValue() {
            String line = DiffAnchorGenerator.anchorLine(0);

            assertThat(line).isEqualTo("# \u2500\u2500\u2500\u2500 sample[0] \u2500\u2500\u2500\u2500 anchor:0dfe8af7 \u2500\u2500\u2500\u2500");
        }

        @Test
        @DisplayName("includes correct sample index")
        void includesCorrectSampleIndex() {
            assertThat(DiffAnchorGenerator.anchorLine(5)).contains("sample[5]");
            assertThat(DiffAnchorGenerator.anchorLine(42)).contains("sample[42]");
        }

        @Test
        @DisplayName("is a YAML comment")
        void isAYamlComment() {
            assertThat(DiffAnchorGenerator.anchorLine(0)).startsWith("#");
        }
    }
}
