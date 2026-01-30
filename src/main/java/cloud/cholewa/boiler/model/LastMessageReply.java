package cloud.cholewa.boiler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LastMessageReply {

    private LocalDateTime timestamp;
    private String message;
}
