package cloud.cholewa.boiler.infrastructure.error.processor;

import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import org.springframework.http.HttpStatus;

import java.util.Collections;

public class BoilerExceptionProcessor implements ExceptionProcessor {

    @Override
    public Errors apply(final Throwable throwable) {
        return Errors.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message("Problem with communication with boiler device")
                    .details(throwable.getMessage())
                    .build()
            ))
            .build();
    }
}
