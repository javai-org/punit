package org.javai.punit.api.typed.spec;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The value produced by
 * {@link Spec#conclude()}. Sealed with two variants:
 * probabilistic tests produce a {@link Verdict verdict}; experiments
 * produce an {@link Artefact artefact} description.
 */
public sealed interface EngineOutcome {

    /**
     * Outcome for a probabilistic test run.
     *
     * @param verdictOutcome the Stage-2 verdict summary
     */
    record ProbabilisticTestVerdict(ProbabilisticTestVerdictOutcome verdictOutcome)
            implements EngineOutcome {
        public ProbabilisticTestVerdict {
            Objects.requireNonNull(verdictOutcome, "verdictOutcome");
        }
    }

    /**
     * Outcome for an experiment run (measure / explore / optimize).
     *
     * @param message human-readable acknowledgement
     * @param destination the path the artefact was or would have been
     *                    written to. Stage 2 does not yet serialise —
     *                    the path is reported symbolically so downstream
     *                    components can verify routing.
     */
    record Artefact(String message, Path destination) implements EngineOutcome {
        public Artefact {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(destination, "destination");
        }
    }
}
