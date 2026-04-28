package org.javai.punit.sentinel.testsubjects;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.legacy.ProbabilisticTest;
import org.javai.punit.api.Sentinel;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * A sentinel spec where all tests pass.
 */
@Sentinel
public class PassingSentinel {

    UseCaseFactory factory = new UseCaseFactory();
    {
        factory.register(StubUseCase.class, StubUseCase::new);
    }

    @ProbabilisticTest(useCase = StubUseCase.class, samples = 10, minPassRate = 0.8)
    @InputSource("instructions")
    void testStub(StubUseCase useCase, String instruction) {
        useCase.execute(instruction).assertContract();
    }

    static Stream<String> instructions() {
        return Stream.of("hello", "world", "foo");
    }
}
