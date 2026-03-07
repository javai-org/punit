package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSourceType;
import org.javai.punit.api.HashableFactorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for factor source type detection based on return type.
 */
@DisplayName("FactorSourceType Detection")
class FactorSourceTypeDetectionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST FACTOR SOURCE METHODS
    // Factor sources are resolved by method name - no annotation needed
    // ═══════════════════════════════════════════════════════════════════════════

    static List<FactorArguments> listSource() {
        return List.of(
                FactorArguments.of("headphones"),
                FactorArguments.of("laptop")
        );
    }

    static Stream<FactorArguments> streamSource() {
        return Stream.of(
                FactorArguments.of("headphones"),
                FactorArguments.of("laptop")
        );
    }

    static Stream<FactorArguments> infiniteStreamSource() {
        return Stream.generate(() -> FactorArguments.of("seed", System.nanoTime()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fromReference")
    class FromReference {

        @Test
        @DisplayName("should detect LIST_CYCLING for List-returning method")
        void shouldDetectListCyclingForListMethod() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "listSource", FactorSourceTypeDetectionTest.class);

            assertThat(source.getSourceType()).isEqualTo(FactorSourceType.LIST_CYCLING);
        }

        @Test
        @DisplayName("should detect STREAM_SEQUENTIAL for Stream-returning method")
        void shouldDetectStreamSequentialForStreamMethod() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source.getSourceType()).isEqualTo(FactorSourceType.STREAM_SEQUENTIAL);
        }

        @Test
        @DisplayName("should detect STREAM_SEQUENTIAL for infinite stream method")
        void shouldDetectStreamSequentialForInfiniteStream() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "infiniteStreamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source.getSourceType()).isEqualTo(FactorSourceType.STREAM_SEQUENTIAL);
        }
    }

    @Nested
    @DisplayName("Implementation Types")
    class ImplementationTypes {

        @Test
        @DisplayName("List source should return DefaultHashableFactorSource")
        void listSourceShouldReturnDefaultImpl() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "listSource", FactorSourceTypeDetectionTest.class);

            assertThat(source).isInstanceOf(DefaultHashableFactorSource.class);
        }

        @Test
        @DisplayName("Stream source should return StreamingHashableFactorSource")
        void streamSourceShouldReturnStreamingImpl() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source).isInstanceOf(StreamingHashableFactorSource.class);
        }
    }

    @Nested
    @DisplayName("Hashing Behavior")
    class HashingBehavior {

        @Test
        @DisplayName("List source should compute content hash")
        void listSourceShouldComputeContentHash() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "listSource", FactorSourceTypeDetectionTest.class);

            String hash1 = source.getSourceHash();
            String hash2 = source.getSourceHash();

            // Hash should be stable
            assertThat(hash1).isEqualTo(hash2);
            // Hash should be non-empty
            assertThat(hash1).isNotEmpty();
        }

        @Test
        @DisplayName("Stream source should compute path hash")
        void streamSourceShouldComputePathHash() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            String hash = source.getSourceHash();

            // Hash should be non-empty and stable
            assertThat(hash).isNotEmpty();
            assertThat(hash).isEqualTo(source.getSourceHash());
        }

        @Test
        @DisplayName("Same stream method should produce same path hash")
        void sameStreamMethodShouldProduceSamePathHash() {
            HashableFactorSource source1 = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);
            HashableFactorSource source2 = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source1.getSourceHash()).isEqualTo(source2.getSourceHash());
        }

        @Test
        @DisplayName("Different stream methods should produce different path hashes")
        void differentStreamMethodsShouldProduceDifferentPathHashes() {
            HashableFactorSource source1 = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);
            HashableFactorSource source2 = FactorSourceAdapter.fromReference(
                    "infiniteStreamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source1.getSourceHash()).isNotEqualTo(source2.getSourceHash());
        }
    }

    @Nested
    @DisplayName("StreamingHashableFactorSource Details")
    class StreamingDetails {

        @Test
        @DisplayName("should expose source path")
        void shouldExposeSourcePath() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            assertThat(source).isInstanceOf(StreamingHashableFactorSource.class);
            StreamingHashableFactorSource streaming = (StreamingHashableFactorSource) source;

            assertThat(streaming.getSourcePath())
                    .contains("FactorSourceTypeDetectionTest")
                    .contains("streamSource");
        }

        @Test
        @DisplayName("should provide fresh stream each time factors() is called")
        void shouldProvideFreshStreamEachTime() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", FactorSourceTypeDetectionTest.class);

            // Consume first stream
            long count1 = source.factors().count();

            // Get second stream - should also have elements
            long count2 = source.factors().count();

            assertThat(count1).isEqualTo(2);
            assertThat(count2).isEqualTo(2);
        }
    }
}

