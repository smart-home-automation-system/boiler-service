package cloud.cholewa.boiler.infrastructure.error.processor;

import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BoilerExceptionProcessorTest {

    private final BoilerExceptionProcessor sut = new BoilerExceptionProcessor();

    @Test
    void should_answer_500_with_the_message_of_the_exception_as_details() {
        Errors errors = sut.apply(new BoilerException("Error controlling furnace"));

        assertThat(errors.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errors.getErrors())
            .singleElement()
            .extracting(ErrorMessage::getMessage, ErrorMessage::getDetails)
            .containsExactly("Problem with communication with boiler device", "Error controlling furnace");
    }

    @Test
    void should_log_the_handled_exception_at_error_level(final CapturedOutput output) {
        sut.apply(new BoilerException("Error controlling furnace"));

        assertThat(output.getOut())
            .contains("ERROR")
            .contains("Handled [BoilerException]: Error controlling furnace");
    }
}
