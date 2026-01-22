package org.javai.punit.contract;

import java.util.Objects;

/**
 * The result of evaluating a postcondition.
 *
 * <p>A postcondition can be in one of three states after evaluation:
 * <ul>
 *   <li>{@link Passed} — The condition was satisfied</li>
 *   <li>{@link Failed} — The condition was not satisfied</li>
 *   <li>{@link Skipped} — The condition was not evaluated (e.g., due to a failed derivation)</li>
 * </ul>
 *
 * <h2>Skipped Postconditions</h2>
 * <p>When a derivation fails, all postconditions that depend on the derived value are
 * skipped. Skipped postconditions are not counted in pass/fail statistics, preventing
 * artificial inflation of failure counts.
 *
 * @see Postcondition
 * @see ServiceContract
 */
public sealed interface PostconditionResult {

    /**
     * Returns the description of the postcondition.
     *
     * @return the postcondition description
     */
    String description();

    /**
     * Returns true if this postcondition passed.
     *
     * @return true if passed, false otherwise
     */
    default boolean passed() {
        return this instanceof Passed;
    }

    /**
     * Returns true if this postcondition failed.
     *
     * @return true if failed, false otherwise
     */
    default boolean failed() {
        return this instanceof Failed;
    }

    /**
     * Returns true if this postcondition was skipped.
     *
     * @return true if skipped, false otherwise
     */
    default boolean skipped() {
        return this instanceof Skipped;
    }

    /**
     * Returns true if this postcondition was evaluated (not skipped).
     *
     * @return true if evaluated, false if skipped
     */
    default boolean wasEvaluated() {
        return !skipped();
    }

    /**
     * A postcondition that passed.
     *
     * @param description the postcondition description
     */
    record Passed(String description) implements PostconditionResult {

        /**
         * Creates a passed result.
         *
         * @param description the postcondition description (must not be null)
         */
        public Passed {
            Objects.requireNonNull(description, "description must not be null");
        }
    }

    /**
     * A postcondition that failed.
     *
     * @param description the postcondition description
     * @param reason the failure reason (optional, may be null)
     */
    record Failed(String description, String reason) implements PostconditionResult {

        /**
         * Creates a failed result.
         *
         * @param description the postcondition description (must not be null)
         * @param reason the failure reason (may be null)
         */
        public Failed {
            Objects.requireNonNull(description, "description must not be null");
        }

        /**
         * Creates a failed result with no specific reason.
         *
         * @param description the postcondition description
         */
        public Failed(String description) {
            this(description, null);
        }
    }

    /**
     * A postcondition that was skipped (not evaluated).
     *
     * <p>Postconditions are skipped when their prerequisite derivation fails.
     * For example, if JSON parsing fails, postconditions that check JSON structure
     * are skipped rather than failed.
     *
     * @param description the postcondition description
     * @param reason the reason for skipping
     */
    record Skipped(String description, String reason) implements PostconditionResult {

        /**
         * Creates a skipped result.
         *
         * @param description the postcondition description (must not be null)
         * @param reason the reason for skipping (must not be null)
         */
        public Skipped {
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
