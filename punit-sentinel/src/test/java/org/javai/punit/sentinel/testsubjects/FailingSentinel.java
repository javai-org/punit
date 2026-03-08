package org.javai.punit.sentinel.testsubjects;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.Sentinel;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * A sentinel spec where all tests fail.
 */
@Sentinel
public class FailingSentinel {

    UseCaseFactory factory = new UseCaseFactory();
    {
        factory.register(FailingUseCase.class, FailingUseCase::new);
    }

    @ProbabilisticTest(useCase = FailingUseCase.class, samples = 10, minPassRate = 0.8)
    @InputSource("inputs")
    void testFailing(FailingUseCase useCase, String input) {
        useCase.execute(input).assertContract();
    }

    static Stream<String> inputs() {
        return Stream.of("a", "b", "c");
    }
}
