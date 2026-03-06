package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("YamlBuilder")
class YamlBuilderTest {

    @Nested
    @DisplayName("rawLine()")
    class RawLineTests {

        @Test
        @DisplayName("emits raw line verbatim with no indentation")
        void emitsRawLineVerbatim() {
            String yaml = YamlBuilder.create()
                .field("before", "value1")
                .rawLine("# this is a raw comment")
                .field("after", "value2")
                .build();

            assertThat(yaml).contains("# this is a raw comment\n");
            assertThat(yaml).contains("before: value1");
            assertThat(yaml).contains("after: value2");
        }

        @Test
        @DisplayName("preserves insertion order among fields")
        void preservesInsertionOrder() {
            String yaml = YamlBuilder.create()
                .field("first", "a")
                .rawLine("# middle comment")
                .field("second", "b")
                .build();

            int firstPos = yaml.indexOf("first: a");
            int commentPos = yaml.indexOf("# middle comment");
            int secondPos = yaml.indexOf("second: b");

            assertThat(firstPos).isLessThan(commentPos);
            assertThat(commentPos).isLessThan(secondPos);
        }

        @Test
        @DisplayName("multiple raw lines preserve order")
        void multipleRawLinesPreserveOrder() {
            String yaml = YamlBuilder.create()
                .rawLine("# first")
                .rawLine("# second")
                .rawLine("# third")
                .build();

            int first = yaml.indexOf("# first");
            int second = yaml.indexOf("# second");
            int third = yaml.indexOf("# third");

            assertThat(first).isLessThan(second);
            assertThat(second).isLessThan(third);
        }

        @Test
        @DisplayName("raw lines inside nested object are emitted correctly")
        void rawLinesInsideNestedObject() {
            String yaml = YamlBuilder.create()
                .startObject("outer")
                    .rawLine("# anchor line")
                    .field("key", "value")
                .endObject()
                .build();

            assertThat(yaml).contains("outer:\n");
            assertThat(yaml).contains("# anchor line\n");
            assertThat(yaml).contains("  key: value");
        }

        @Test
        @DisplayName("surrounding fields serialize correctly with raw line present")
        void surroundingFieldsSerializeCorrectly() {
            String yaml = YamlBuilder.create()
                .startObject("data")
                    .field("a", 1)
                    .rawLine("# divider")
                    .field("b", 2)
                .endObject()
                .build();

            assertThat(yaml).contains("  a: 1");
            assertThat(yaml).contains("  b: 2");
            assertThat(yaml).contains("# divider");
        }
    }
}
