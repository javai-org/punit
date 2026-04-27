package org.javai.punit.engine.baseline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the {@code factorsFingerprint} field carried by every
 * {@link BaselineRecord} and used by the resolver as one of the
 * three exact-match lookup keys.
 *
 * <p>The fingerprint is the first eight hex characters of
 * {@code SHA-256(String.valueOf(factors))}. Records'
 * {@code toString()} is deterministic per the JLS, so the
 * fingerprint is stable across runs and processes given a
 * record-typed factors value.
 *
 * <p>{@code null} factors produces the literal string {@code "null"}
 * — distinct from any hex fingerprint, so a measure with no factors
 * cannot collide with a measure under any actual factor value.
 *
 * <p>See {@code docs/DES-BASELINE-YAML-SCHEMA.md} §"factorsFingerprint"
 * for the design rationale.
 */
public final class FactorsFingerprint {

    private static final int HEX_PREFIX_LENGTH = 8;
    private static final String NULL_FINGERPRINT = "null";

    private FactorsFingerprint() { }

    /**
     * @return the fingerprint of {@code factors} — the first eight
     *         hex characters of {@code SHA-256(String.valueOf(factors))},
     *         or the literal {@code "null"} when {@code factors} is null.
     */
    public static String of(Object factors) {
        if (factors == null) {
            return NULL_FINGERPRINT;
        }
        byte[] hash = sha256(String.valueOf(factors));
        return HexFormat.of().formatHex(hash).substring(0, HEX_PREFIX_LENGTH);
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK 8+; this branch is unreachable.
            throw new AssertionError("SHA-256 unavailable on this JVM", e);
        }
    }
}
