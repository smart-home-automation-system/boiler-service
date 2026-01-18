package cloud.cholewa.boiler.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LastMessage {
    private LocalDateTime timestamp;
    private String message;

    public LastMessage(String message) {
        this.timestamp = LocalDateTime.now();
        this.message = message;
    }
}
