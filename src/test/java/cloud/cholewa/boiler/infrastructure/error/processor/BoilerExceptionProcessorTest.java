package cloud.cholewa.boiler.infrastructure.error.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BoilerExceptionProcessorTest {

    private final Logger processorLogger = (Logger) LoggerFactory.getLogger(BoilerExceptionProcessor.class);
    private final ListAppender<ILoggingEvent> processorLog = new ListAppender<>();

    private final BoilerExceptionProcessor sut = new BoilerExceptionProcessor();

    @BeforeEach
    void setUp() {
        processorLog.start();
        processorLogger.addAppender(processorLog);
    }

    @AfterEach
    void tearDown() {
        processorLogger.detachAppender(processorLog);
    }

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
    void should_log_the_handled_exception_at_error_level() {
        sut.apply(new BoilerException("Error controlling furnace"));

        assertThat(processorLog.list)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                    .isEqualTo("Handled [BoilerException]: Error controlling furnace");
            });
    }
}
