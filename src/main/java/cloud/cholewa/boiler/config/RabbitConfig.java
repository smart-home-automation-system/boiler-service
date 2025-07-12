package cloud.cholewa.boiler.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    @Value("${spring.rabbitmq.consumer-name:undefined}")
    private String consumerName;

    @Bean
    ConnectionNameStrategy connectionNameStrategy() {
        return connectionFactory -> consumerName;
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
