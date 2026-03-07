package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.javai.outcome.Outcome;
import org.javai.punit.contract.match.ResultExtractor;
import org.javai.punit.contract.match.StringMatcher;
import org.javai.punit.contract.match.VerificationMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseOutcome")
class UseCaseOutcomeTest {

    record TestInput(String value, int number) {}

    private static final ServiceContract<TestInput, String> CONTRACT = ServiceContract
            .<TestInput, String>define()
            .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","was empty") : Outcome.ok())
            .ensure("Reasonable length", s -> s.length() < 1000 ? Outcome.ok() : Outcome.fail("check","too long"))
            .derive("Uppercase", s -> Outcome.ok(s.toUpperCase()))
                .ensure("All caps", s -> s.equals(s.toUpperCase()) ? Outcome.ok() : Outcome.fail("check","not all caps"))
            .build();

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("produces outcome with result")
        void producesOutcomeWithResult() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result: " + in.value())
                    .build();

            assertThat(outcome.result()).isEqualTo("result: hello");
        }

        @Test
        @DisplayName("captures execution time")
        void capturesExecutionTime() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "result";
                    })
                    .build();

            assertThat(outcome.executionTime()).isGreaterThanOrEqualTo(Duration.ofMillis(50));
            assertThat(outcome.executionTime()).isLessThan(Duration.ofMillis(200));
        }

        @Test
        @DisplayName("captures timestamp at execution start")
        void capturesTimestampAtExecutionStart() {
            java.time.Instant before = java.time.Instant.now();
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();
            java.time.Instant after = java.time.Instant.now();

            assertThat(outcome.timestamp()).isAfterOrEqualTo(before);
            assertThat(outcome.timestamp()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("stores metadata")
        void storesMetadata() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("tokensUsed", 150)
                    .meta("model", "gpt-4")
                    .build();

            assertThat(outcome.metadata()).containsEntry("tokensUsed", 150);
            assertThat(outcome.metadata()).containsEntry("model", "gpt-4");
        }

        @Test
        @DisplayName("metadata is immutable")
        void metadataIsImmutable() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("key", "value")
                    .build();

            assertThatThrownBy(() -> outcome.metadata().put("newKey", "newValue"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("throws when contract is null")
        void throwsWhenContractIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome.withContract(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("contract must not be null");
        }

        @Test
        @DisplayName("throws when execute function is null")
        void throwsWhenExecuteFunctionIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("function must not be null");
        }

        @Test
        @DisplayName("throws when meta key is null")
        void throwsWhenMetaKeyIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta(null, "value"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("key must not be null");
        }

        @Test
        @DisplayName("allows null meta value")
        void allowsNullMetaValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("nullableField", null)
                    .build();

            assertThat(outcome.metadata()).containsEntry("nullableField", null);
        }

        @Test
        @DisplayName("withResult extracts metadata from result")
        void withResultExtractsMetadataFromResult() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result-" + in.number())
                    .withResult((result, meta) -> meta
                            .meta("resultLength", result.length())
                            .meta("containsNumber", result.contains("42")))
                    .build();

            assertThat(outcome.metadata()).containsEntry("resultLength", 9);
            assertThat(outcome.metadata()).containsEntry("containsNumber", true);
        }

        @Test
        @DisplayName("withResult chains with meta")
        void withResultChainsWithMeta() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .withResult((result, meta) -> meta.meta("fromResult", result.toUpperCase()))
                    .meta("staticValue", "test")
                    .build();

            assertThat(outcome.metadata()).containsEntry("fromResult", "RESULT");
            assertThat(outcome.metadata()).containsEntry("staticValue", "test");
        }

        @Test
        @DisplayName("withResult can be called multiple times")
        void withResultCanBeCalledMultipleTimes() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .withResult((result, meta) -> meta.meta("length", result.length()))
                    .withResult((result, meta) -> meta.meta("upper", result.toUpperCase()))
                    .build();

            assertThat(outcome.metadata()).containsEntry("length", 6);
            assertThat(outcome.metadata()).containsEntry("upper", "RESULT");
        }

        @Test
        @DisplayName("withResult throws when extractor is null")
        void withResultThrowsWhenExtractorIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .withResult(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("extractor must not be null");
        }

        @Test
        @DisplayName("withResult provides access to actual result value")
        void withResultProvidesAccessToActualResultValue() {
            String[] capturedResult = {null};

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "computed-" + in.value())
                    .withResult((result, meta) -> {
                        capturedResult[0] = result;
                        meta.meta("captured", true);
                    })
                    .build();

            assertThat(capturedResult[0]).isEqualTo("computed-hello");
            assertThat(outcome.result()).isEqualTo("computed-hello");
        }
    }

    @Nested
    @DisplayName("metadata accessors")
    class MetadataAccessorTests {

        @Test
        @DisplayName("getMetadataLong returns value for first matching key")
        void getMetadataLongReturnsValueForFirstMatchingKey() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("tokens", 150)
                    .build();

            assertThat(outcome.getMetadataLong("tokensUsed", "tokens", "totalTokens"))
                    .hasValue(150L);
        }

        @Test
        @DisplayName("getMetadataLong tries multiple keys in order")
        void getMetadataLongTriesMultipleKeysInOrder() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("totalTokens", 200)
                    .build();

            assertThat(outcome.getMetadataLong("tokensUsed", "tokens", "totalTokens"))
                    .hasValue(200L);
        }

        @Test
        @DisplayName("getMetadataLong returns empty when no key matches")
        void getMetadataLongReturnsEmptyWhenNoKeyMatches() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(outcome.getMetadataLong("tokensUsed", "tokens"))
                    .isEmpty();
        }

        @Test
        @DisplayName("getMetadataLong handles Integer values")
        void getMetadataLongHandlesIntegerValues() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("tokensUsed", Integer.valueOf(100))
                    .build();

            assertThat(outcome.getMetadataLong("tokensUsed")).hasValue(100L);
        }

        @Test
        @DisplayName("getMetadataString returns value when present")
        void getMetadataStringReturnsValueWhenPresent() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("model", "gpt-4")
                    .build();

            assertThat(outcome.getMetadataString("model")).hasValue("gpt-4");
        }

        @Test
        @DisplayName("getMetadataString returns empty when not present")
        void getMetadataStringReturnsEmptyWhenNotPresent() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(outcome.getMetadataString("model")).isEmpty();
        }

        @Test
        @DisplayName("getMetadataString returns empty for non-string value")
        void getMetadataStringReturnsEmptyForNonStringValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("model", 123)
                    .build();

            assertThat(outcome.getMetadataString("model")).isEmpty();
        }

        @Test
        @DisplayName("getMetadataBoolean returns value when present")
        void getMetadataBooleanReturnsValueWhenPresent() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("cached", true)
                    .build();

            assertThat(outcome.getMetadataBoolean("cached")).hasValue(true);
        }

        @Test
        @DisplayName("getMetadataBoolean returns empty when not present")
        void getMetadataBooleanReturnsEmptyWhenNotPresent() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(outcome.getMetadataBoolean("cached")).isEmpty();
        }

        @Test
        @DisplayName("getMetadataBoolean returns empty for non-boolean value")
        void getMetadataBooleanReturnsEmptyForNonBooleanValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("cached", "yes")
                    .build();

            assertThat(outcome.getMetadataBoolean("cached")).isEmpty();
        }
    }

    @Nested
    @DisplayName("postconditions")
    class PostconditionTests {

        @Test
        @DisplayName("evaluates postconditions through contract")
        void evaluatesPostconditionsThroughContract() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello")
                    .build();

            List<PostconditionResult> results = outcome.evaluatePostconditions();

            // 2 direct + 1 derivation + 1 nested = 4
            assertThat(results).hasSize(4);
            assertThat(results).allMatch(PostconditionResult::passed);
        }

        @Test
        @DisplayName("returns postcondition count from contract")
        void returnsPostconditionCountFromContract() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(outcome.postconditionCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("allPostconditionsSatisfied returns true when all pass")
        void allPostconditionsSatisfiedReturnsTrueWhenAllPass() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello")
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isTrue();
        }

        @Test
        @DisplayName("allPostconditionsSatisfied returns false when any fails")
        void allPostconditionsSatisfiedReturnsFalseWhenAnyFails() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "")  // Empty string fails "Not empty"
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isFalse();
        }

        @Test
        @DisplayName("postconditions are evaluated lazily on each call")
        void postconditionsAreEvaluatedLazilyOnEachCall() {
            int[] evaluationCount = {0};

            ServiceContract<TestInput, String> countingContract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Counting", s -> {
                        evaluationCount[0]++;
                        return Outcome.ok();
                    })
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(countingContract)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(evaluationCount[0]).isZero();

            outcome.evaluatePostconditions();
            assertThat(evaluationCount[0]).isEqualTo(1);

            outcome.evaluatePostconditions();
            assertThat(evaluationCount[0]).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("returns descriptive string")
        void returnsDescriptiveString() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("tokens", 100)
                    .build();

            assertThat(outcome.toString())
                    .contains("UseCaseOutcome")
                    .contains("result")
                    .contains("tokens");
        }
    }

    @Nested
    @DisplayName("instance conformance")
    class InstanceConformanceTests {

        @Test
        @DisplayName("outcome without expecting() has no expected value")
        void outcomeWithoutExpectingHasNoExpectedValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .build();

            assertThat(outcome.hasExpectedValue()).isFalse();
            assertThat(outcome.matchesExpected()).isTrue();  // No expectation = trivially satisfied
            assertThat(outcome.getMatchResult()).isEmpty();
        }

        @Test
        @DisplayName("expecting() with matching value")
        void expectingWithMatchingValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "expected result")
                    .expecting("expected result", StringMatcher.exact())
                    .build();

            assertThat(outcome.hasExpectedValue()).isTrue();
            assertThat(outcome.matchesExpected()).isTrue();
            assertThat(outcome.getMatchResult()).isPresent();
            assertThat(outcome.getMatchResult().get().matches()).isTrue();
        }

        @Test
        @DisplayName("expecting() with non-matching value")
        void expectingWithNonMatchingValue() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "actual result")
                    .expecting("expected result", StringMatcher.exact())
                    .build();

            assertThat(outcome.hasExpectedValue()).isTrue();
            assertThat(outcome.matchesExpected()).isFalse();
            assertThat(outcome.getMatchResult()).isPresent();
            assertThat(outcome.getMatchResult().get().matches()).isFalse();
            assertThat(outcome.getMatchResult().get().diff())
                    .contains("expected result")
                    .contains("actual result");
        }

        @Test
        @DisplayName("expecting() with extractor")
        void expectingWithExtractor() {
            record Response(String content, int tokens) {}
            ResultExtractor<Response, String> extractor = Response::content;

            ServiceContract<TestInput, Response> responseContract = ServiceContract
                    .<TestInput, Response>define()
                    .ensure("Has content", r -> Outcome.ok())
                    .build();

            UseCaseOutcome<Response> outcome = UseCaseOutcome
                    .withContract(responseContract)
                    .input(new TestInput("hello", 42))
                    .execute(in -> new Response("hello world", 10))
                    .expecting("hello world", extractor, StringMatcher.exact())
                    .build();

            assertThat(outcome.matchesExpected()).isTrue();
        }

        @Test
        @DisplayName("expecting() with identity extractor")
        void expectingWithIdentityExtractor() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .expecting("result", ResultExtractor.identity(), StringMatcher.exact())
                    .build();

            assertThat(outcome.matchesExpected()).isTrue();
        }

        @Test
        @DisplayName("fullySatisfied() when postconditions pass and expected matches")
        void fullySatisfiedWhenBothPass() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello")  // Non-empty, reasonable length
                    .expecting("hello", StringMatcher.exact())
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isTrue();
            assertThat(outcome.matchesExpected()).isTrue();
            assertThat(outcome.fullySatisfied()).isTrue();
        }

        @Test
        @DisplayName("fullySatisfied() returns false when postconditions fail")
        void fullySatisfiedFalseWhenPostconditionsFail() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "")  // Empty string fails "Not empty"
                    .expecting("", StringMatcher.exact())  // But expected matches
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isFalse();
            assertThat(outcome.matchesExpected()).isTrue();
            assertThat(outcome.fullySatisfied()).isFalse();
        }

        @Test
        @DisplayName("fullySatisfied() returns false when expected mismatches")
        void fullySatisfiedFalseWhenExpectedMismatches() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello")  // Postconditions pass
                    .expecting("world", StringMatcher.exact())  // But expected doesn't match
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isTrue();
            assertThat(outcome.matchesExpected()).isFalse();
            assertThat(outcome.fullySatisfied()).isFalse();
        }

        @Test
        @DisplayName("fullySatisfied() returns true when no expectation and postconditions pass")
        void fullySatisfiedTrueWhenNoExpectationAndPostconditionsPass() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello")
                    .build();

            assertThat(outcome.fullySatisfied()).isTrue();
        }

        @Test
        @DisplayName("expecting() throws when extractor is null")
        void expectingThrowsWhenExtractorIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .expecting("expected", null, StringMatcher.exact()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("extractor must not be null");
        }

        @Test
        @DisplayName("expecting() throws when matcher is null")
        void expectingThrowsWhenMatcherIsNull() {
            assertThatThrownBy(() -> UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .expecting("expected", ResultExtractor.identity(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matcher must not be null");
        }

        @Test
        @DisplayName("expecting() with custom matcher")
        void expectingWithCustomMatcher() {
            VerificationMatcher<String> containsMatcher = (expected, actual) -> {
                if (actual != null && actual.contains(expected)) {
                    return VerificationMatcher.MatchResult.match();
                }
                return VerificationMatcher.MatchResult.mismatch(
                        "Expected to contain '" + expected + "' but was '" + actual + "'");
            };

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "hello world")
                    .expecting("world", containsMatcher)
                    .build();

            assertThat(outcome.matchesExpected()).isTrue();
        }

        @Test
        @DisplayName("expecting() with ignoreCase matcher")
        void expectingWithIgnoreCaseMatcher() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "HELLO")
                    .expecting("hello", StringMatcher.ignoreCase())
                    .build();

            assertThat(outcome.matchesExpected()).isTrue();
        }

        @Test
        @DisplayName("expecting() can be combined with meta()")
        void expectingCanBeCombinedWithMeta() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .meta("key", "value")
                    .expecting("result", StringMatcher.exact())
                    .meta("anotherKey", "anotherValue")
                    .build();

            assertThat(outcome.metadata()).containsEntry("key", "value");
            assertThat(outcome.metadata()).containsEntry("anotherKey", "anotherValue");
            assertThat(outcome.matchesExpected()).isTrue();
        }

        @Test
        @DisplayName("expecting() can be combined with withResult()")
        void expectingCanBeCombinedWithWithResult() {
            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(new TestInput("hello", 42))
                    .execute(in -> "result")
                    .withResult((r, m) -> m.meta("length", r.length()))
                    .expecting("result", StringMatcher.exact())
                    .build();

            assertThat(outcome.metadata()).containsEntry("length", 6);
            assertThat(outcome.matchesExpected()).isTrue();
        }
    }
}
