package org.javai.punit.engine.explore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PerConfigSummary;
import org.javai.punit.engine.Engine;
import org.javai.punit.runtime.ExploreEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Pure / in-memory tests for the EX05 explore output writer.
 *
 * <p>The writer is exercised both directly ({@link #writeYamlAndFilenameForOneConfig})
 * and through {@link ExploreEmitter}'s in-memory sink overload
 * ({@link #emitterCapturesOnePerConfig}) — neither test touches
 * disk.
 */
@DisplayName("ExploreOutputWriter — EX05 schema, filename, end-to-end via in-memory sink")
class ExploreOutputWriterTest {

    record LlmFactors(String model, double temperature) {}

    /** Always-passing use case. Output type is the input length, for trivial sampling. */
    private static class LengthUseCase implements UseCase<LlmFactors, String, Integer> {
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }

    @Test
    @DisplayName("writeYaml emits the EX05 schema with factors / execution / statistics / cost / resultProjection blocks")
    void writeYamlAndFilenameForOneConfig() {
        // Drive a 1-config explore through the engine to produce a
        // real PerConfigSummary, then feed the writer directly.
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new LengthUseCase())
                .inputs("a", "bb")
                .samples(2)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(new LlmFactors("gpt-4o", 0.3)))
                .build();
        new Engine().run(experiment);

        PerConfigSummary<?, ?> entry = experiment.perConfigSummaries().get(0);
        FactorBundle bundle = FactorBundle.of(entry.factors());

        ExploreOutputWriter writer = new ExploreOutputWriter();
        String yaml = writer.writeYaml("LengthUseCase", bundle, entry);

        Map<String, Object> parsed = new Yaml().load(yaml);
        assertThat(parsed).containsKeys(
                "schemaVersion", "useCaseId", "generatedAt",
                "factors", "execution", "statistics", "cost", "resultProjection");
        assertThat(parsed).containsEntry("schemaVersion", "punit-spec-1");
        assertThat(parsed).containsEntry("useCaseId", "LengthUseCase");

        @SuppressWarnings("unchecked")
        Map<String, Object> factors = (Map<String, Object>) parsed.get("factors");
        assertThat(factors).containsEntry("model", "gpt-4o");
        assertThat(factors).containsEntry("temperature", 0.3);

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) parsed.get("execution");
        assertThat(execution).containsKey("samplesPlanned");
        assertThat(execution).containsKey("samplesExecuted");
        assertThat(execution).containsKey("terminationReason");

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) parsed.get("statistics");
        assertThat(statistics).containsKeys("observed", "successes", "failures", "failureDistribution");

        @SuppressWarnings("unchecked")
        Map<String, Object> projection = (Map<String, Object>) parsed.get("resultProjection");
        // 2 samples — one entry per sample, keyed by sample[N].
        assertThat(projection).containsKeys("sample[0]", "sample[1]");
        @SuppressWarnings("unchecked")
        Map<String, Object> sample0 = (Map<String, Object>) projection.get("sample[0]");
        // EX07 per-sample fields: input, postconditions, executionTimeMs;
        // content present on success, failureDetail on failure (LengthUseCase
        // never fails so content is the expected key here).
        assertThat(sample0).containsKeys("input", "postconditions", "executionTimeMs", "content");
    }

    @Test
    @DisplayName("filenameFor produces a readable stem from {field}-{value} pairs joined by _")
    void filenameForReadableStem() {
        ExploreOutputWriter writer = new ExploreOutputWriter();
        FactorBundle bundle = FactorBundle.of(new LlmFactors("gpt-4o", 0.3));
        String stem = writer.filenameFor(bundle);
        assertThat(stem).isEqualTo("model-gpt-4o_temperature-0.3");
    }

    @Test
    @DisplayName("filenameFor truncates long values and appends a 4-char SHA-256 hash")
    void filenameForTruncatesLongValues() {
        record PromptFactors(String systemPrompt) {}
        ExploreOutputWriter writer = new ExploreOutputWriter();
        FactorBundle bundle = FactorBundle.of(new PromptFactors(
                "You are a helpful shopping assistant answering customer queries"));
        String stem = writer.filenameFor(bundle);
        // First 24 chars of the value, sanitised, then "-" + 4 hex.
        // Chars 0..23 of the prompt (counting from 0): "You are a helpful shoppi".
        assertThat(stem).startsWith("systemPrompt-You_are_a_helpful_shoppi-");
        assertThat(stem).matches("systemPrompt-.{24}-[0-9a-f]{4}");
    }

    @Test
    @DisplayName("ExploreEmitter writes one entry per FT to the in-memory sink, keyed by useCaseId/{stem}.yaml")
    void emitterCapturesOnePerConfig() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new IdUseCase("explore-test"))
                .inputs("x", "y")
                .samples(2)
                .build();
        Experiment experiment = Experiment.exploring(sampling)
                .grid(List.of(
                        new LlmFactors("gpt-4o", 0.3),
                        new LlmFactors("gpt-4o", 0.7)))
                .build();
        new Engine().run(experiment);

        Map<String, String> sink = new LinkedHashMap<>();
        BiConsumer<String, String> capture = sink::put;
        ExploreEmitter.emit(experiment, capture);

        assertThat(sink).hasSize(2);
        assertThat(sink).containsKeys(
                "explore-test/model-gpt-4o_temperature-0.3.yaml",
                "explore-test/model-gpt-4o_temperature-0.7.yaml");
        // Each captured value parses as YAML carrying the EX05 schema header.
        for (String yaml : sink.values()) {
            Map<String, Object> parsed = new Yaml().load(yaml);
            assertThat(parsed).containsEntry("schemaVersion", "punit-spec-1");
            assertThat(parsed).containsEntry("useCaseId", "explore-test");
        }
    }

    @Test
    @DisplayName("ExploreEmitter rejects non-EXPLORE experiments")
    void emitterRejectsWrongKind() {
        Sampling<LlmFactors, String, Integer> sampling = Sampling
                .<LlmFactors, String, Integer>builder()
                .useCaseFactory(f -> new LengthUseCase())
                .inputs("a")
                .samples(1)
                .build();
        Experiment measure = Experiment.measuring(sampling, new LlmFactors("gpt-4o", 0.3)).build();

        try {
            ExploreEmitter.emit(measure, new HashMap<String, String>()::put);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("EXPLORE");
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for non-EXPLORE experiment");
    }

    /** Use case with a configured id, useful for asserting on the emitted relative path. */
    private static final class IdUseCase implements UseCase<LlmFactors, String, Integer> {
        private final String id;
        IdUseCase(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }
}
