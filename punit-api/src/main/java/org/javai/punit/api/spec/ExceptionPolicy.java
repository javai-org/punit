package org.javai.punit.api.spec;

import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseOutcome;

/**
 * How the engine treats a thrown exception from
 * {@link UseCase#apply(Object)}.
 *
 * <p>Under the typed authoring model, exceptions are reserved for
 * defects — programming mistakes, misconfiguration, catastrophe —
 * while expected business failures return
 * {@link UseCaseOutcome#fail(String, String)
 * UseCaseOutcome.fail(...)}. Silently converting a thrown exception
 * into a counted sample failure therefore hides a bug, so the default
 * is {@link #ABORT_TEST}. Authors who want the legacy behaviour opt
 * into {@link #FAIL_SAMPLE} explicitly.
 *
 * <p>{@link Error} subtypes (OOM, StackOverflow, LinkageError) always
 * propagate regardless of policy — they are never caught.
 */
public enum ExceptionPolicy {

    /**
     * A defect from {@code apply()} bubbles out of the engine; the run
     * dies. This is the default.
     */
    ABORT_TEST,

    /**
     * A defect from {@code apply()} is caught, counted as a failed
     * sample, and the run continues. Opt-in for authors who want the
     * legacy exception-as-failure semantics.
     */
    FAIL_SAMPLE
}
