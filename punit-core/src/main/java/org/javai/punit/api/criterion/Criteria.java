package org.javai.punit.api.criterion;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionBuilder;

/**
 * Factory entry points for declaring criteria. Two shapes:
 *
 * <ul>
 *   <li>{@link #direct(String, Consumer) direct(id, body)} — a
 *       criterion whose postconditions evaluate against the
 *       contract's output. No transform; per-sample outcome is
 *       restricted to {@link CriterionSampleOutcome#PASS PASS} or
 *       {@link CriterionSampleOutcome#FAIL FAIL}.</li>
 *   <li>{@link #transforming(String, Function, Consumer)
 *       transforming(id, transform, body)} — a criterion that first
 *       transforms the contract's output to a derived form, then
 *       evaluates postconditions against the derived value.
 *       Transform failure (the function returns
 *       {@link Outcome.Fail} or throws) classifies the sample
 *       {@link CriterionSampleOutcome#INCONCLUSIVE INCONCLUSIVE};
 *       the postcondition chain is not evaluated.</li>
 * </ul>
 *
 * <p>The two factories cover the methodology's two criterion shapes
 * and structurally enforce the one-transform-per-criterion cap: a
 * criterion is produced by exactly one factory call, with either
 * zero or one transform. No API expresses two transforms in one
 * criterion.
 *
 * <pre>{@code
 * Criterion<ChatResponse> hasContent = Criteria.direct(
 *         "has-content",
 *         b -> b.ensure("non-empty", r -> r.content().isEmpty()
 *                 ? Outcome.fail("empty", "")
 *                 : Outcome.ok()));
 *
 * Criterion<ChatResponse> validJson = Criteria.transforming(
 *         "valid-json",
 *         ChatResponse::parseJson,
 *         b -> b.ensure("has operations", JsonNode::hasOperations)
 *               .ensure("operations valid", JsonNode::operationsValid));
 * }</pre>
 */
public final class Criteria {

    private Criteria() {
        // utility class — no instances
    }

    /**
     * Declare a criterion whose postcondition chain evaluates
     * directly against the contract's output. No transform.
     *
     * @param id   the criterion's stable identifier; must be non-null
     *             and non-blank
     * @param body a consumer that populates a {@link PostconditionBuilder}
     *             with the criterion's postcondition chain. The
     *             builder is fresh; the consumer's calls to
     *             {@code ensure(...)} populate it.
     * @param <O>  the contract's per-sample output value type
     * @return a criterion ready to be added to a {@link CriteriaBuilder}
     */
    public static <O> Criterion<O> direct(
            String id, Consumer<PostconditionBuilder<O>> body) {
        validateId(id);
        Objects.requireNonNull(body, "body");
        PostconditionBuilder<O> b = new PostconditionBuilder<>();
        body.accept(b);
        return new DirectCriterion<>(id, b.build());
    }

    /**
     * Declare a criterion that first transforms the contract's
     * output {@code O} to a derived form {@code D}, then evaluates
     * its postcondition chain against the derived value.
     *
     * <p>Transform failure (the function returns
     * {@link Outcome.Fail} or throws a {@link RuntimeException})
     * classifies the sample INCONCLUSIVE for this criterion; the
     * postcondition chain is not evaluated. The transform's failure
     * — name and message — is preserved on the per-sample result for
     * diagnostics.
     *
     * @param id        the criterion's stable identifier; must be
     *                  non-null and non-blank
     * @param transform the pre-postcondition transform; receives the
     *                  contract's produced output and returns an
     *                  outcome over the derived type
     * @param body      a consumer that populates a
     *                  {@link PostconditionBuilder} typed over the derived
     *                  type. The builder is fresh; the consumer's
     *                  calls to {@code ensure(...)} populate it.
     * @param <O>       the contract's per-sample output value type
     * @param <D>       the derived value type the postcondition chain
     *                  evaluates against
     * @return a criterion ready to be added to a {@link CriteriaBuilder}
     */
    public static <O, D> Criterion<O> transforming(
            String id,
            Function<O, Outcome<D>> transform,
            Consumer<PostconditionBuilder<D>> body) {
        validateId(id);
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(body, "body");
        PostconditionBuilder<D> b = new PostconditionBuilder<>();
        body.accept(b);
        return new TransformingCriterion<>(id, transform, b.build());
    }

    private static void validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
