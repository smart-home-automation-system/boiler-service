package cloud.cholewa.boiler.furnace;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class FurnaceService {

    @RabbitListener(queues = "furnace.status", id = "asd")
    public void test(final Message message, final Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        log.info(
            "Received Furnace Status Message from RabbitMQ message: {}",
            new String(message.getBody(), StandardCharsets.UTF_8)
        );
        log.info("Received Furnace Status Message from RabbitMQ from channel {}", channel.toString());
        log.info("Received Furnace Status Message from RabbitMQ from tag {}", tag);
    }


}
