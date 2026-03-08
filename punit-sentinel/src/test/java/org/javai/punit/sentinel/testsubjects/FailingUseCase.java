package org.javai.punit.sentinel.testsubjects;

import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;

/**
 * A use case that always fails its postcondition.
 */
@UseCase("failing-use-case")
public class FailingUseCase {

    private static final ServiceContract<String, String> CONTRACT =
            ServiceContract.<String, String>define()
                    .ensure("always fails", result -> org.javai.outcome.Outcome.fail("check", "always fails"))
                    .build();

    public UseCaseOutcome<String> execute(String input) {
        return UseCaseOutcome.<String, String>withContract(CONTRACT)
                .input(input)
                .execute(i -> "FAIL: " + i)
                .build();
    }
}
