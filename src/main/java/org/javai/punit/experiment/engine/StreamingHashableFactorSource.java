package org.javai.punit.experiment.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.javai.punit.experiment.api.FactorArguments;
import org.javai.punit.experiment.api.FactorSourceType;
import org.javai.punit.experiment.api.HashableFactorSource;

/**
 * A {@link HashableFactorSource} implementation for streaming (non-materializing) factor sources.
 *
 * <p>This implementation is designed for factor sources that return {@code Stream<FactorArguments>}
 * and should be consumed sequentially without materialization. Key characteristics:
 *
 * <ul>
 *   <li><b>No materialization</b>: Stream elements are consumed on-demand, not cached</li>
 *   <li><b>Sequential consumption</b>: Each sample consumes the next stream element</li>
 *   <li><b>Path-based hash</b>: Hash is computed from source identity (class + method),
 *       not from factor values</li>
 *   <li><b>Constant memory</b>: Memory usage doesn't grow with factor count</li>
 * </ul>
 *
 * <h2>Path Hash</h2>
 * <p>Unlike {@link DefaultHashableFactorSource} which hashes factor values, this implementation
 * computes a hash from the source's "path" (identity):
 * <pre>
 * SHA-256(className + "#" + methodName + attributes)
 * </pre>
 *
 * <p>This means two streaming sources are considered equivalent if they have the same
 * identity (class and method), regardless of the specific values they produce.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Generated inputs (random seeds, sequential IDs)</li>
 *   <li>Large or infinite factor sets</li>
 *   <li>Memory-constrained environments</li>
 *   <li>Probabilistic algorithms requiring unique inputs per sample</li>
 * </ul>
 *
 * @see DefaultHashableFactorSource
 * @see FactorSourceType#STREAM_SEQUENTIAL
 */
public class StreamingHashableFactorSource implements HashableFactorSource {

    private final String sourceName;
    private final String sourcePath;
    private final Supplier<Stream<FactorArguments>> streamSupplier;
    private final String cachedHash;

    /**
     * Creates a new StreamingHashableFactorSource.
     *
     * @param sourceName     the name of this factor source (for logging/display)
     * @param sourcePath     the full path identifying this source (e.g., "ClassName#methodName")
     * @param streamSupplier a supplier that provides a fresh stream of factor arguments
     */
    public StreamingHashableFactorSource(
            String sourceName,
            String sourcePath,
            Supplier<Stream<FactorArguments>> streamSupplier) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName must not be null");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier must not be null");
        this.cachedHash = computePathHash(sourcePath);
    }

    @Override
    public Stream<FactorArguments> factors() {
        // Each call returns a fresh stream—no caching, no materialization
        return streamSupplier.get();
    }

    @Override
    public String getSourceHash() {
        // Path-based hash is computed once at construction
        return cachedHash;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public FactorSourceType getSourceType() {
        return FactorSourceType.STREAM_SEQUENTIAL;
    }

    /**
     * Returns the full path identifying this source.
     *
     * @return the source path (e.g., "ClassName#methodName")
     */
    public String getSourcePath() {
        return sourcePath;
    }

    private static String computePathHash(String path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            digest.update(pathBytes);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "StreamingHashableFactorSource{" +
                "sourceName='" + sourceName + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", hash='" + cachedHash.substring(0, 8) + "..." + '\'' +
                '}';
    }
}

