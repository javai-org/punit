package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultHashableFactorSource")
class DefaultHashableFactorSourceTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should require non-null source name")
        void shouldRequireNonNullSourceName() {
            assertThatThrownBy(() -> new DefaultHashableFactorSource(null, Stream::empty))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceName");
        }

        @Test
        @DisplayName("should require non-null factor supplier")
        void shouldRequireNonNullFactorSupplier() {
            assertThatThrownBy(() -> new DefaultHashableFactorSource("test", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("factorSupplier");
        }

        @Test
        @DisplayName("should accept valid parameters")
        void shouldAcceptValidParameters() {
            var source = new DefaultHashableFactorSource("test", Stream::empty);
            assertThat(source.getSourceName()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("fromList factory method")
    class FromListFactory {

        @Test
        @DisplayName("should create source from list")
        void shouldCreateSourceFromList() {
            var factors = List.of(
                    FactorArguments.of("a", 1),
                    FactorArguments.of("b", 2)
            );

            var source = DefaultHashableFactorSource.fromList("testSource", factors);

            assertThat(source.getSourceName()).isEqualTo("testSource");
            assertThat(source.factors().toList()).hasSize(2);
        }

        @Test
        @DisplayName("should pre-compute hash")
        void shouldPreComputeHash() {
            var factors = List.of(FactorArguments.of("a", 1));
            var source = DefaultHashableFactorSource.fromList("testSource", factors);

            // Hash should be available immediately (pre-computed)
            assertThat(source.getSourceHash()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should reject null factors list")
        void shouldRejectNullFactorsList() {
            assertThatThrownBy(() -> DefaultHashableFactorSource.fromList("test", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Hash computation")
    class HashComputation {

        @Test
        @DisplayName("should compute SHA-256 hash")
        void shouldComputeSha256Hash() {
            var source = DefaultHashableFactorSource.fromList("test", List.of(
                    FactorArguments.of("value1"),
                    FactorArguments.of("value2")
            ));

            var hash = source.getSourceHash();

            // SHA-256 produces 64 hex characters
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("should produce same hash for same factors")
        void shouldProduceSameHashForSameFactors() {
            var factors = List.of(
                    FactorArguments.of("a", 1),
                    FactorArguments.of("b", 2)
            );

            var source1 = DefaultHashableFactorSource.fromList("s1", factors);
            var source2 = DefaultHashableFactorSource.fromList("s2", factors);

            assertThat(source1.getSourceHash()).isEqualTo(source2.getSourceHash());
        }

        @Test
        @DisplayName("should produce different hash for different factors")
        void shouldProduceDifferentHashForDifferentFactors() {
            var source1 = DefaultHashableFactorSource.fromList("s1", List.of(
                    FactorArguments.of("a", 1)
            ));
            var source2 = DefaultHashableFactorSource.fromList("s2", List.of(
                    FactorArguments.of("a", 2)
            ));

            assertThat(source1.getSourceHash()).isNotEqualTo(source2.getSourceHash());
        }

        @Test
        @DisplayName("should produce different hash for different ordering")
        void shouldProduceDifferentHashForDifferentOrdering() {
            var source1 = DefaultHashableFactorSource.fromList("s1", List.of(
                    FactorArguments.of("a"),
                    FactorArguments.of("b")
            ));
            var source2 = DefaultHashableFactorSource.fromList("s2", List.of(
                    FactorArguments.of("b"),
                    FactorArguments.of("a")
            ));

            assertThat(source1.getSourceHash()).isNotEqualTo(source2.getSourceHash());
        }

        @Test
        @DisplayName("should produce consistent hash for empty source")
        void shouldProduceConsistentHashForEmptySource() {
            var source1 = DefaultHashableFactorSource.fromList("s1", List.of());
            var source2 = DefaultHashableFactorSource.fromList("s2", List.of());

            assertThat(source1.getSourceHash()).isEqualTo(source2.getSourceHash());
        }
    }

    @Nested
    @DisplayName("Lazy materialization and caching")
    class LazyMaterializationAndCaching {

        @Test
        @DisplayName("should not invoke supplier until needed")
        void shouldNotInvokeSupplierUntilNeeded() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            var source = new DefaultHashableFactorSource("test", () -> {
                invocationCount.incrementAndGet();
                return Stream.of(FactorArguments.of("a"));
            });

            assertThat(invocationCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("should invoke supplier once on factors() call")
        void shouldInvokeSupplierOnceOnFactorsCall() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            var source = new DefaultHashableFactorSource("test", () -> {
                invocationCount.incrementAndGet();
                return Stream.of(FactorArguments.of("a"));
            });

            source.factors().toList();
            source.factors().toList();
            source.factors().toList();

            assertThat(invocationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should invoke supplier once on getSourceHash() call")
        void shouldInvokeSupplierOnceOnHashCall() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            var source = new DefaultHashableFactorSource("test", () -> {
                invocationCount.incrementAndGet();
                return Stream.of(FactorArguments.of("a"));
            });

            source.getSourceHash();
            source.getSourceHash();
            source.getSourceHash();

            assertThat(invocationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should share materialization between factors() and getSourceHash()")
        void shouldShareMaterializationBetweenMethods() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            var source = new DefaultHashableFactorSource("test", () -> {
                invocationCount.incrementAndGet();
                return Stream.of(FactorArguments.of("a"));
            });

            source.getSourceHash();  // Triggers materialization
            source.factors().toList();  // Should use cached list

            assertThat(invocationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("factors() then getSourceHash() should share materialization")
        void factorsThenHashShouldShareMaterialization() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            var source = new DefaultHashableFactorSource("test", () -> {
                invocationCount.incrementAndGet();
                return Stream.of(FactorArguments.of("a"));
            });

            source.factors().toList();  // Triggers materialization
            source.getSourceHash();  // Should use cached result

            assertThat(invocationCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getFactorCount()")
    class GetFactorCount {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            var source = DefaultHashableFactorSource.fromList("test", List.of(
                    FactorArguments.of("a"),
                    FactorArguments.of("b"),
                    FactorArguments.of("c")
            ));

            assertThat(source.getFactorCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return zero for empty source")
        void shouldReturnZeroForEmptySource() {
            var source = DefaultHashableFactorSource.fromList("test", List.of());
            assertThat(source.getFactorCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should include source name")
        void shouldIncludeSourceName() {
            var source = DefaultHashableFactorSource.fromList("mySource", List.of(
                    FactorArguments.of("a")
            ));

            assertThat(source.toString()).contains("mySource");
        }

        @Test
        @DisplayName("should include truncated hash")
        void shouldIncludeTruncatedHash() {
            var source = DefaultHashableFactorSource.fromList("test", List.of(
                    FactorArguments.of("a")
            ));

            // Hash should be truncated to first 8 chars + "..."
            assertThat(source.toString()).containsPattern("[0-9a-f]{8}\\.\\.\\.");
        }

        @Test
        @DisplayName("should include factor count")
        void shouldIncludeFactorCount() {
            var source = DefaultHashableFactorSource.fromList("test", List.of(
                    FactorArguments.of("a"),
                    FactorArguments.of("b")
            ));

            assertThat(source.toString()).contains("factorCount=2");
        }
    }
}

