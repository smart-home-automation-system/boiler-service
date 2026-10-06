package cloud.cholewa.boiler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//the connection factory and the RabbitTemplate are deliberately left to the auto-configuration:
//only the template built by RabbitTemplateConfigurer gets spring.rabbitmq.template.* applied
//(mandatory, observation), and RabbitAutoConfiguration backs off from a bean of either type.
//This service has one connection, to the notification virtual host, so it needs no second factory
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class RabbitConfig {

    //the name the broker shows for the connection: the pod, which already says which service it
    //is and tells the old pod from the new one during a rollout
    @Bean
    ConnectionNameStrategy connectionNameStrategy(
        @Value("${HOSTNAME:}") final String hostname,
        @Value("${spring.application.name}") final String service
    ) {
        final String name = connectionName(hostname, service);
        return connectionFactory -> name;
    }

    //Kubernetes names a pod after its Deployment and puts that name into HOSTNAME. Anything else
    //found there - nothing, an empty value, a workstation, a container id - is not a pod of this
    //service and would not say who is connected
    static String connectionName(final String hostname, final String service) {
        return hostname.startsWith(service + "-") ? hostname : service + "-local";
    }

    //a message matching no binding comes back instead of being dropped without a word; the
    //publisher fails the send on it, this is the line that says why
    @Bean
    RabbitTemplateCustomizer returnedNotificationLogger() {
        return rabbitTemplate -> rabbitTemplate.setReturnsCallback(returned -> log.error(
            "Notification was not routed to any queue: {} - {}", returned.getReplyCode(), returned.getReplyText()));
    }
}
