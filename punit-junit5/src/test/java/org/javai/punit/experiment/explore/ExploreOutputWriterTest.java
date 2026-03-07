package org.javai.punit.experiment.explore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javai.punit.experiment.engine.output.DiffAnchorGenerator;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.javai.punit.experiment.model.ResultProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ExploreOutputWriter.
 *
 * <p>Verifies that explore output:
 * <ul>
 *   <li>Omits requirements section (minPassRate)</li>
 *   <li>Omits inferential statistics (standardError, confidenceInterval95)</li>
 *   <li>Includes descriptive statistics (observed, successes, failures)</li>
 *   <li>Includes failure distribution for qualitative insight</li>
 *   <li>Emits diff anchor lines before each sample block</li>
 * </ul>
 */
@DisplayName("ExploreOutputWriter")
class ExploreOutputWriterTest {

    private ExploreOutputWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ExploreOutputWriter();
    }

    @Nested
    @DisplayName("Statistics Output")
    class StatisticsOutput {

        @Test
        @DisplayName("should include observed success rate")
        void shouldIncludeObservedSuccessRate() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("observed: 0.7000");
        }

        @Test
        @DisplayName("should include success and failure counts")
        void shouldIncludeSuccessAndFailureCounts() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("successes: 14")
                .contains("failures: 6");
        }

        @Test
        @DisplayName("should NOT include standardError")
        void shouldNotIncludeStandardError() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("standardError");
        }

        @Test
        @DisplayName("should NOT include confidenceInterval95")
        void shouldNotIncludeConfidenceInterval() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("confidenceInterval95");
        }

        @Test
        @DisplayName("should include failure distribution when present")
        void shouldIncludeFailureDistributionWhenPresent() {
            Map<String, Integer> failures = new LinkedHashMap<>();
            failures.put("TIMEOUT", 3);
            failures.put("VALIDATION_ERROR", 3);
            EmpiricalBaseline baseline = createExploreBaselineWithFailures(0.70, 14, 6, failures);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("failureDistribution:")
                .contains("TIMEOUT: 3")
                .contains("VALIDATION_ERROR: 3");
        }
    }

    @Nested
    @DisplayName("Requirements Section")
    class RequirementsSection {

        @Test
        @DisplayName("should NOT include requirements section")
        void shouldNotIncludeRequirementsSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("requirements:");
        }

        @Test
        @DisplayName("should NOT include minPassRate")
        void shouldNotIncludeMinPassRate() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("minPassRate");
        }
    }

    @Nested
    @DisplayName("Common Sections")
    class CommonSections {

        @Test
        @DisplayName("should include schemaVersion")
        void shouldIncludeSchemaVersion() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("schemaVersion: punit-spec-1");
        }

        @Test
        @DisplayName("should include useCaseId")
        void shouldIncludeUseCaseId() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("useCaseId: TestUseCase");
        }

        @Test
        @DisplayName("should include execution section")
        void shouldIncludeExecutionSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("execution:")
                .contains("samplesPlanned: 20")
                .contains("samplesExecuted: 20");
        }

        @Test
        @DisplayName("should include cost section")
        void shouldIncludeCostSection() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("cost:")
                .contains("totalTimeMs:")
                .contains("totalTokens:");
        }

        @Test
        @DisplayName("should include contentFingerprint")
        void shouldIncludeContentFingerprint() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).containsPattern("contentFingerprint: [a-f0-9]{64}");
        }
    }

    @Nested
    @DisplayName("Output Format")
    class OutputFormat {

        @Test
        @DisplayName("explore output has valid YAML structure")
        void exploreOutputHasValidYamlStructure() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("schemaVersion:")
                .contains("useCaseId:")
                .contains("statistics:")
                .contains("observed:")
                .contains("contentFingerprint:");
        }

        @Test
        @DisplayName("explore output is distinct from measure output")
        void exploreOutputIsDistinctFromMeasureOutput() {
            EmpiricalBaseline baseline = createExploreBaseline(0.70, 14, 6);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .as("EXPLORE output should NOT have requirements (comparative, not prescriptive)")
                .doesNotContain("requirements:")
                .doesNotContain("minPassRate:");

            assertThat(yaml)
                .as("EXPLORE output should NOT have inferential stats (unreliable with small samples)")
                .doesNotContain("standardError:")
                .doesNotContain("confidenceInterval95:");
        }
    }

    @Nested
    @DisplayName("Diff Anchors")
    class DiffAnchors {

        @Test
        @DisplayName("emits anchor line before each sample block")
        void emitsAnchorLineBeforeEachSampleBlock() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input0", "content0"),
                createProjection(1, "input1", "content1"),
                createProjection(2, "input2", "content2")
            );

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains(DiffAnchorGenerator.anchorLine(0))
                .contains(DiffAnchorGenerator.anchorLine(1))
                .contains(DiffAnchorGenerator.anchorLine(2));
        }

        @Test
        @DisplayName("anchor values are deterministic across calls")
        void anchorValuesAreDeterministic() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "content")
            );

            String yaml1 = writer.toYaml(baseline);
            String yaml2 = writer.toYaml(baseline);

            assertThat(yaml1).isEqualTo(yaml2);
        }

        @Test
        @DisplayName("does not contain diffableContent key")
        void doesNotContainDiffableContentKey() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "some content")
            );

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("diffableContent");
        }

        @Test
        @DisplayName("does not contain absent placeholders")
        void doesNotContainAbsentPlaceholders() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "content")
            );

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("<absent>");
        }

        @Test
        @DisplayName("emits content as block scalar")
        void emitsContentAsBlockScalar() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "full response content here")
            );

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("content: |");
            assertThat(yaml).contains("full response content here");
        }

        @Test
        @DisplayName("emits sample[N] as YAML key for structure")
        void emitsSampleNAsYamlKey() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "content"),
                createProjection(1, "input", "content")
            );

            String yaml = writer.toYaml(baseline);

            // sample[N]: keys provide YAML structure; anchors provide diff sync
            assertThat(yaml).contains("sample[0]:");
            assertThat(yaml).contains("sample[1]:");
        }

        @Test
        @DisplayName("handles variable-length sample blocks")
        void handlesVariableLengthSampleBlocks() {
            ResultProjection short0 = new ResultProjection(
                0, "input", Map.of("check", "passed"), 10, "short", null
            );
            ResultProjection long1 = new ResultProjection(
                1, "input", Map.of("check", "failed"), 20,
                "line1\nline2\nline3\nline4\nline5", "some failure"
            );

            EmpiricalBaseline baseline = createBaselineWithProjections(short0, long1);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains(DiffAnchorGenerator.anchorLine(0))
                .contains(DiffAnchorGenerator.anchorLine(1));
            assertThat(yaml).contains("failureDetail: some failure");
        }

        @Test
        @DisplayName("includes postconditions in output")
        void includesPostconditionsInOutput() {
            ResultProjection projection = new ResultProjection(
                0, "Add apples",
                Map.of("Valid actions", "passed", "Has content", "passed"),
                50, "response content", null
            );

            EmpiricalBaseline baseline = createBaselineWithProjections(projection);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("postconditions:")
                .contains("Valid actions: passed")
                .contains("Has content: passed");
        }

        @Test
        @DisplayName("omits content field when content is null")
        void omitsContentFieldWhenContentIsNull() {
            ResultProjection projection = new ResultProjection(
                0, "input", Map.of("Execution completed", "failed"),
                100, null, "RuntimeException: boom"
            );

            EmpiricalBaseline baseline = createBaselineWithProjections(projection);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("content: |");
            assertThat(yaml).contains("failureDetail:");
        }

        @Test
        @DisplayName("every sample has its own data block after its anchor")
        void everySampleHasOwnDataBlock() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                new ResultProjection(0, "input-zero", Map.of("check", "passed"), 10, "content-zero", null),
                new ResultProjection(1, "input-one", Map.of("check", "failed"), 20, "content-one", "error-one"),
                new ResultProjection(2, "input-two", Map.of("check", "passed"), 30, "content-two", null)
            );

            String yaml = writer.toYaml(baseline);

            // Each sample's unique data must appear between its anchor and the next anchor
            String anchor0 = DiffAnchorGenerator.anchorLine(0);
            String anchor1 = DiffAnchorGenerator.anchorLine(1);
            String anchor2 = DiffAnchorGenerator.anchorLine(2);

            int pos0 = yaml.indexOf(anchor0);
            int pos1 = yaml.indexOf(anchor1);
            int pos2 = yaml.indexOf(anchor2);

            // Anchors appear in order
            assertThat(pos0).isLessThan(pos1);
            assertThat(pos1).isLessThan(pos2);

            // Sample 0's data is between anchor 0 and anchor 1
            String block0 = yaml.substring(pos0, pos1);
            assertThat(block0).contains("input-zero").contains("content-zero");

            // Sample 1's data is between anchor 1 and anchor 2
            String block1 = yaml.substring(pos1, pos2);
            assertThat(block1).contains("input-one").contains("content-one").contains("error-one");

            // Sample 2's data is after anchor 2
            String block2 = yaml.substring(pos2);
            assertThat(block2).contains("input-two").contains("content-two");
        }

        @Test
        @DisplayName("anchor lines are YAML comments")
        void anchorLinesAreYamlComments() {
            EmpiricalBaseline baseline = createBaselineWithProjections(
                createProjection(0, "input", "content")
            );

            String yaml = writer.toYaml(baseline);

            // Every anchor line starts with #
            String anchorLine = DiffAnchorGenerator.anchorLine(0);
            assertThat(anchorLine).startsWith("#");
            assertThat(yaml).contains(anchorLine);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private ResultProjection createProjection(int index, String input, String content) {
        return new ResultProjection(
            index, input, Map.of("check", "passed"), 0, content, null
        );
    }

    private EmpiricalBaseline createBaselineWithProjections(ResultProjection... projections) {
        int total = 20;
        StatisticsSummary stats = new StatisticsSummary(
            0.70, 0.1, 0.5, 0.9, 14, 6, Map.of()
        );

        return EmpiricalBaseline.builder()
            .useCaseId("TestUseCase")
            .generatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .execution(new ExecutionSummary(total, total, "COMPLETED", null))
            .statistics(stats)
            .cost(new CostSummary(100, 5, 2000, 100))
            .resultProjections(List.of(projections))
            .build();
    }

    private EmpiricalBaseline createExploreBaseline(
            double successRate, int successes, int failures) {
        return createExploreBaselineWithFailures(successRate, successes, failures, Map.of());
    }

    private EmpiricalBaseline createExploreBaselineWithFailures(
            double successRate, int successes, int failures,
            Map<String, Integer> failureDistribution) {

        int total = successes + failures;
        double se = Math.sqrt(successRate * (1 - successRate) / total);
        double ciLower = Math.max(0, successRate - 1.96 * se);
        double ciUpper = Math.min(1, successRate + 1.96 * se);

        StatisticsSummary stats = new StatisticsSummary(
            successRate, se, ciLower, ciUpper, successes, failures, failureDistribution
        );

        return EmpiricalBaseline.builder()
            .useCaseId("TestUseCase")
            .generatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .execution(new ExecutionSummary(total, total, "COMPLETED", null))
            .statistics(stats)
            .cost(new CostSummary(100, 5, 2000, 100))
            .build();
    }
}
