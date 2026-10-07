package cloud.cholewa.boiler.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the notifications are published. The broker, the virtual host and the user are those of
 * {@code spring.rabbitmq.*} - publishing notifications is all this service does there.<br>
 * {@code env} - the value of the {@code env} header the exchange routes by: {@code prod} or {@code dev}.
 */
@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    @NotBlank String exchange,
    @NotBlank String env
) {
}
