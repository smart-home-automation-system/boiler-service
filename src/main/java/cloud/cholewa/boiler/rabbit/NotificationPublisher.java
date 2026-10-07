package cloud.cholewa.boiler.rabbit;

import cloud.cholewa.boiler.config.NotificationProperties;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    static final String CATEGORY_HEADER = "category";
    static final String ENV_HEADER = "env";
    static final String LEVEL_HEADER = "level";
    static final String CATEGORY_ALERT = "alert";
    static final String CATEGORY_INFO = "info";
    static final String LEVEL_ERROR = "error";
    static final String LEVEL_WARN = "warn";
    static final String LEVEL_INFO = "info";
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitOperations rabbitOperations;
    private final NotificationProperties properties;

    /** Something went wrong: shown in red. */
    public Mono<Void> publishAlert(final String text) {
        return publish(CATEGORY_ALERT, LEVEL_ERROR, text);
    }

    /** It is still wrong: the same queue as the alert, shown in yellow. */
    public Mono<Void> publishReminder(final String text) {
        return publish(CATEGORY_ALERT, LEVEL_WARN, text);
    }

    /** It is fine again: shown in green. */
    public Mono<Void> publishInfo(final String text) {
        return publish(CATEGORY_INFO, LEVEL_INFO, text);
    }

    //a headers exchange: the routing key is ignored, the queue is chosen by category and env.
    //The level is not routed by - notification-service reads it to pick the color on Discord.
    //Plain text, because notification-service reads the message as a String.
    //Completes only when the broker has taken the message into a queue: a send that returns says
    //nothing about that, and the caller records the notification as sent
    private Mono<Void> publish(final String category, final String level, final String text) {
        return Mono.fromCallable(() -> {
                final CorrelationData correlationData = new CorrelationData();
                rabbitOperations.send(properties.exchange(), "", toMessage(category, level, text), correlationData);
                return correlationData;
            })
            //the send blocks on the broker connection
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(correlationData -> Mono.fromFuture(correlationData.getFuture())
                .timeout(CONFIRM_TIMEOUT)
                .flatMap(confirm -> verifyDelivery(correlationData, confirm)))
            .doOnSuccess(unused -> log.info("Published {} notification ({}): {}", category, level, text));
    }

    private Mono<Void> verifyDelivery(final CorrelationData correlationData, final CorrelationData.Confirm confirm) {
        if (!confirm.ack()) {
            return Mono.error(new BoilerException("Notification refused by the broker: " + confirm.reason()));
        }
        //a message matching no binding is confirmed all the same - the broker hands it back first
        if (correlationData.getReturned() != null) {
            return Mono.error(new BoilerException(
                "Notification was not routed to any queue: " + correlationData.getReturned().getReplyText()));
        }
        return Mono.empty();
    }

    private Message toMessage(final String category, final String level, final String text) {
        return MessageBuilder.withBody(text.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
            .setContentEncoding(StandardCharsets.UTF_8.name())
            .setHeader(CATEGORY_HEADER, category)
            .setHeader(LEVEL_HEADER, level)
            .setHeader(ENV_HEADER, properties.env())
            .build();
    }
}
