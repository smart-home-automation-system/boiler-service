package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.WaterClientConfig;
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
public class WaterClient {

    private final WaterClientConfig waterClientConfig;
    private final WebClient waterWebClient;

    Mono<SystemActiveReply> querySystemActive() {
        return waterWebClient.get()
            .uri(waterClientConfig::getUriBuilder)
            .retrieve()
            .bodyToMono(SystemActiveReply.class)
            .doOnError(ex -> log.error("Error while querying water-service for active status {}", ex.getMessage()))
            .doOnSubscribe(subscription -> log.info("Querying water-service for active status"))
            .onErrorMap(ex -> new BoilerException("Failed to query water-service active status"));
    }
}
