package org.javai.punit.api.typed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * A content-addressable projection of a factor record into the
 * canonical form consumed by the baseline spec YAML and by the
 * {@code factorBundleHash} segment of the baseline filename.
 *
 * <p>The bundle is produced from a Java {@code record} — the canonical
 * factor-record shape in the typed model. The record's components are
 * traversed in declaration order; each component value is lifted via
 * {@link FactorValue#of(Object)} and the resulting pairs make up
 * {@link #entries()}.
 *
 * <p>{@link #canonicalJson()} serialises the bundle with keys sorted
 * alphabetically, no whitespace, booleans as the literals {@code true}
 * / {@code false}, numbers in minimal decimal form, and strings /
 * temporals / URIs / enums as JSON-quoted strings. {@link #bundleHash()}
 * is the first four hex characters of {@code SHA-256(canonicalJson())}.
 *
 * <p>The empty bundle — produced by a record with no components — has
 * {@link #canonicalJson()} = {@code "{}"} and
 * {@link #bundleHash()} = the four-hex-char truncation of
 * {@code SHA-256("{}")}. The baseline-filename convention treats an
 * empty factor bundle by omitting the {@code factorBundleHash}
 * segment, not by emitting the hash of {@code "{}"}; that
 * filename-layer rule belongs to the serialiser, not here.
 */
public final class FactorBundle {

    private static final FactorBundle EMPTY = new FactorBundle(List.of());

    private final List<Entry> entries;

    private FactorBundle(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Name / raw-value pair. Exposed for inspection and YAML emission. */
    public record Entry(String name, FactorValue value) {}

    /**
     * The empty bundle — no factor components.
     *
     * @return the empty bundle
     */
    public static FactorBundle empty() {
        return EMPTY;
    }

    /**
     * Constructs a bundle by reflectively reading the components of
     * {@code factorRecord}.
     *
     * @param factorRecord a Java record instance, non-null
     * @return the bundle
     * @throws NullPointerException if {@code factorRecord} is null
     * @throws IllegalArgumentException if {@code factorRecord} is not a record,
     *         or if any component has a type not permitted by
     *         {@link FactorValue}.
     */
    public static <FT> FactorBundle of(FT factorRecord) {
        if (factorRecord == null) {
            throw new NullPointerException("factorRecord");
        }
        Class<?> type = factorRecord.getClass();
        if (!type.isRecord()) {
            throw new IllegalArgumentException(
                    "factor type must be a record, got "
                            + type.getName());
        }
        RecordComponent[] components = type.getRecordComponents();
        if (components.length == 0) {
            return EMPTY;
        }
        List<Entry> out = new ArrayList<>(components.length);
        for (RecordComponent comp : components) {
            Object raw;
            try {
                var accessor = comp.getAccessor();
                accessor.setAccessible(true);
                raw = accessor.invoke(factorRecord);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(
                        "failed to read record component " + comp.getName()
                                + " of " + type.getName(), e);
            }
            FactorValue wrapped;
            try {
                wrapped = FactorValue.of(raw);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "factor record " + type.getName()
                                + " has inadmissible component '"
                                + comp.getName() + "': " + e.getMessage(), e);
            }
            out.add(new Entry(comp.getName(), wrapped));
        }
        return new FactorBundle(out);
    }

    /**
     * @return the entries in record-component declaration order
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * @return {@code true} when the bundle has no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * The canonical JSON form — keys sorted alphabetically, no
     * whitespace, minimal numeric representation, strings /
     * temporals / URIs / enums as JSON-quoted strings.
     *
     * @return the canonical JSON string
     */
    public String canonicalJson() {
        if (entries.isEmpty()) {
            return "{}";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Entry e : entries) {
            sorted.put(e.name(), e.value().canonical());
        }
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (var kv : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(StringValue.jsonQuote(kv.getKey()));
            out.append(':');
            out.append(kv.getValue());
        }
        out.append('}');
        return out.toString();
    }

    /**
     * @return the first four hex characters of {@code SHA-256(canonicalJson())}
     */
    public String bundleHash() {
        byte[] digest = sha256(canonicalJson());
        char[] hex = new char[4];
        for (int i = 0; i < 2; i++) {
            int b = digest[i] & 0xff;
            hex[2 * i] = HEX[b >>> 4];
            hex[2 * i + 1] = HEX[b & 0x0f];
        }
        return new String(hex);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 must be supported by every JRE", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FactorBundle other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "FactorBundle" + canonicalJson();
    }
}
