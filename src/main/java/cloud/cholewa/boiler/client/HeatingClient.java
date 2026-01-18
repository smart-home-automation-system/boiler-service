package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.HeatingClientConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.home.model.SystemActiveReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeatingClient {

    private final HeatingClientConfig heatingClientConfig;
    private final WebClient heatingWebClient;

    Mono<SystemActiveReply> querySystemActive() {
        return heatingWebClient.get()
            .uri(heatingClientConfig::getUriBuilder)
            .retrieve()
            .bodyToMono(SystemActiveReply.class)
            .doOnError(ex -> log.error("Error while querying system active status {}", ex.getMessage()))
            .doOnSubscribe(subscription -> log.debug("Subscribing to system active status query"))
            .onErrorMap(ex -> new BoilerException("Error calling heating system"));
    }
}
