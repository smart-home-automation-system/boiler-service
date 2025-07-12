package cloud.cholewa.boiler.shelly;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellyClient {

    private final ShellyConfig shellyConfig;
    private final WebClient webClient;

    public Mono<Void> enableCirculationPump() {
        return webClient
            .get()
            .uri(uriBuilder -> shellyConfig.getUriBuilder(uriBuilder).path("relay/1")
                .queryParam("turn", "on")
                .build())
            .retrieve()
            .bodyToMono(Void.class);
    }
}
