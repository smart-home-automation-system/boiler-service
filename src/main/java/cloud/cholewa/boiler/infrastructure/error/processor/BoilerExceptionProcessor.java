package cloud.cholewa.boiler.infrastructure.error.processor;

import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Collections;

@Slf4j
public class BoilerExceptionProcessor implements ExceptionProcessor {

    @Override
    public Errors apply(final Throwable throwable) {
        log.error("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage());

        return Errors.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message("Problem with communication with boiler device")
                    //a BoilerException carries only the fixed text ShellyClient gives it - what the
                    //device answered is logged there and never becomes part of the message
                    .details(throwable.getMessage())
                    .build()
            ))
            .build();
    }
}
