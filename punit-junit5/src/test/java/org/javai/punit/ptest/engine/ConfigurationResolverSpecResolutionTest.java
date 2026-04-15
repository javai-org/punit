package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.model.LatencyBaseline;
import org.javai.punit.spec.registry.SpecRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigurationResolver spec resolution")
class ConfigurationResolverSpecResolutionTest {

	private static LatencyBaseline syntheticBaseline(int n, long maxMs) {
		long[] sorted = new long[n];
		for (int i = 0; i < n; i++) {
			sorted[i] = Math.round(maxMs * ((i + 1) / (double) n));
		}
		return new LatencyBaseline(sorted, maxMs / 2, sorted[n - 1]);
	}

	@Nested
	@DisplayName("loadLatencySpec")
	class LoadLatencySpec {

		@Test
		@DisplayName("returns dedicated latency spec when available")
		void returnsDedicatedLatencySpec() {
			ExecutionSpecification latencySpec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase.latency")
					.latencyBaseline(syntheticBaseline(100, 1400))
					.empiricalBasis(100, 95)
					.build();

			SpecRepository repo = specId -> {
				if ("TestUseCase.latency".equals(specId)) {
					return Optional.of(latencySpec);
				}
				return Optional.empty();
			};

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadLatencySpec("TestUseCase");

			assertThat(result).isPresent();
			assertThat(result.get().hasLatencyBaseline()).isTrue();
			assertThat(result.get().getLatencyBaseline().sampleCount()).isEqualTo(100);
		}

		@Test
		@DisplayName("falls back to combined spec with latency data")
		void fallsBackToCombinedSpecWithLatencyData() {
			ExecutionSpecification combinedSpec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase")
					.latencyBaseline(syntheticBaseline(100, 1400))
					.empiricalBasis(100, 95)
					.build();

			SpecRepository repo = specId -> {
				if ("TestUseCase".equals(specId)) {
					return Optional.of(combinedSpec);
				}
				return Optional.empty();
			};

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadLatencySpec("TestUseCase");

			assertThat(result).isPresent();
			assertThat(result.get().hasLatencyBaseline()).isTrue();
		}

		@Test
		@DisplayName("returns empty when combined spec has no latency data")
		void returnsEmptyWhenCombinedSpecHasNoLatencyData() {
			ExecutionSpecification combinedSpec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase")
					.empiricalBasis(100, 95)
					.build();

			SpecRepository repo = specId -> {
				if ("TestUseCase".equals(specId)) {
					return Optional.of(combinedSpec);
				}
				return Optional.empty();
			};

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadLatencySpec("TestUseCase");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("returns empty for null spec ID")
		void returnsEmptyForNullSpecId() {
			SpecRepository repo = specId -> Optional.empty();

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadLatencySpec(null);

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("prefers dedicated latency spec over combined")
		void prefersDedicatedLatencySpecOverCombined() {
			ExecutionSpecification dedicatedLatencySpec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase.latency")
					.latencyBaseline(syntheticBaseline(200, 1500))
					.empiricalBasis(200, 190)
					.build();

			ExecutionSpecification combinedSpec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase")
					.latencyBaseline(syntheticBaseline(100, 1400))
					.empiricalBasis(100, 95)
					.build();

			SpecRepository repo = specId -> switch (specId) {
				case "TestUseCase.latency" -> Optional.of(dedicatedLatencySpec);
				case "TestUseCase" -> Optional.of(combinedSpec);
				default -> Optional.empty();
			};

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadLatencySpec("TestUseCase");

			assertThat(result).isPresent();
			// Verify it's the dedicated spec (has 200 samples, not 100)
			assertThat(result.get().getEmpiricalBasis().samples()).isEqualTo(200);
		}
	}

	@Nested
	@DisplayName("loadSpec")
	class LoadSpec {

		@Test
		@DisplayName("resolves spec from injected repository")
		void resolvesSpecFromInjectedRepository() {
			ExecutionSpecification spec = ExecutionSpecification.builder()
					.useCaseId("TestUseCase")
					.empiricalBasis(1000, 950)
					.build();

			SpecRepository repo = specId ->
					"TestUseCase".equals(specId) ? Optional.of(spec) : Optional.empty();

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadSpec("TestUseCase");

			assertThat(result).isPresent();
			assertThat(result.get().getEmpiricalBasis().samples()).isEqualTo(1000);
		}

		@Test
		@DisplayName("returns empty for unknown spec")
		void returnsEmptyForUnknownSpec() {
			SpecRepository repo = specId -> Optional.empty();

			ConfigurationResolver resolver = new ConfigurationResolver(repo);
			Optional<ExecutionSpecification> result = resolver.loadSpec("Unknown");

			assertThat(result).isEmpty();
		}
	}
}
