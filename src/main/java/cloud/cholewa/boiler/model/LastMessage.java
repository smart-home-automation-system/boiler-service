package cloud.cholewa.boiler.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LastMessage {
    private LocalDateTime timestamp;
    private String message;
}
