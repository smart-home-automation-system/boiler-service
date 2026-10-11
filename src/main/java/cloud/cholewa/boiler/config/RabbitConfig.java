package cloud.cholewa.boiler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//the connection factory and the RabbitTemplate are deliberately left to the auto-configuration:
//only the template built by RabbitTemplateConfigurer gets spring.rabbitmq.template.* applied
//(mandatory, observation), and RabbitAutoConfiguration backs off from a bean of either type.
//This service has one connection, to the notification virtual host, so it needs no second factory.
//The publisher of the notifications and the name of the connection - the pod - come from
//cholewa-commons (cholewa.notification.* in application.yaml); what is left here is the one thing
//the library leaves to the application, because a template takes a single returns callback
@Slf4j
@Configuration
public class RabbitConfig {

    //a message matching no binding comes back instead of being dropped without a word; the
    //publisher fails the send on it, this is the line that says why
    @Bean
    RabbitTemplateCustomizer returnedNotificationLogger() {
        return rabbitTemplate -> rabbitTemplate.setReturnsCallback(returned -> log.error(
            "Notification was not routed to any queue: {} - {}", returned.getReplyCode(), returned.getReplyText()));
    }
}
