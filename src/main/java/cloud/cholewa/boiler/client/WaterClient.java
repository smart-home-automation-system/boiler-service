package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.WaterClientConfig;
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

    public Mono<SystemActiveReply> querySystemActive() {
        return waterWebClient.get()
            .uri(waterClientConfig::getUriBuilder)
            .retrieve()
            .bodyToMono(SystemActiveReply.class)
            .doOnSubscribe(subscription -> log.info("Querying water-service for active status"))
            .doOnError(ex -> log.error("Error while querying water-service for active status {}", ex.getMessage()))
            .onErrorResume(ex -> {
                log.warn("Returning default water system active status: false");
                return Mono.just(SystemActiveReply.builder().active(false).build());
            });
    }
}
