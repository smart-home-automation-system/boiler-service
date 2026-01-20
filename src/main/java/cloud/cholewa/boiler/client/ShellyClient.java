package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.ShellyConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyPro4StatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static cloud.cholewa.boiler.model.BoilerDeviceType.FURNACE;
import static cloud.cholewa.boiler.model.BoilerDeviceType.HEATING;
import static cloud.cholewa.boiler.model.BoilerDeviceType.HOT_WATER;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellyClient {

    private final ShellyConfig shellyConfig;
    private final WebClient shellyWebClient;

    public Mono<ShellyPro4StatusResponse> getWaterPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HOT_WATER).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .doOnError(throwable -> log.error("Error fetching water pump status", throwable))
            .onErrorMap(throwable -> new BoilerException("Error fetching water pump status"));
    }

    public Mono<Relay> controlWaterPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HOT_WATER)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .doOnError(throwable -> log.error("Error controlling water pump", throwable))
            .onErrorMap(throwable -> new BoilerException("Error controlling water pump"));
    }

    public Mono<ShellyPro4StatusResponse> getHeatingPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HEATING).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .doOnError(throwable -> log.error("Error fetching heating pump status", throwable))
            .onErrorMap(throwable -> new BoilerException("Error fetching heating pump status"));
    }

    public Mono<Relay> controlHeatingPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HEATING)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .doOnError(throwable -> log.error("Error controlling heating pump", throwable))
            .onErrorMap(throwable -> new BoilerException("Error controlling heating pump"));
    }

    public Mono<ShellyPro4StatusResponse> getFurnaceStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, FURNACE).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .doOnError(throwable -> log.error("Error fetching furnace status", throwable))
            .onErrorMap(throwable -> new BoilerException("Error fetching furnace status"));
    }

    public Mono<Relay> controlFurnace(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, FURNACE)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .doOnError(throwable -> log.error("Error controlling furnace", throwable))
            .onErrorMap(throwable -> new BoilerException("Error controlling furnace"));
    }
}
