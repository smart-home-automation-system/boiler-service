package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.ShellyConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
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

    public Mono<Relay> controlWaterPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HOT_WATER)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError, clientResponse -> Mono.error(
                    new BoilerException("Hot Water pump issue check IP: " + shellyConfig.getBoilerHost())
                )
            )
            .bodyToMono(Relay.class)
            .onErrorResume(Exception.class, ex -> Mono.error(new BoilerException(ex.getMessage())));
    }

    public Mono<ShellyProRelayResponse> getWaterPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HOT_WATER).build())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError, clientResponse -> Mono.error(
                    new BoilerException("Hot Water pump issue check IP: " + shellyConfig.getBoilerHost())
                )
            )
            .bodyToMono(ShellyProRelayResponse.class)
            .onErrorResume(Exception.class, ex -> Mono.error(new BoilerException(ex.getMessage())));

    }

    public Mono<Relay> controlHeatingPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HEATING)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError, clientResponse -> Mono.error(
                    new BoilerException("Heating pump issue check IP: " + shellyConfig.getBoilerHost())
                )
            )
            .bodyToMono(Relay.class)
            .onErrorResume(Exception.class, ex -> Mono.error(new BoilerException(ex.getMessage())));
    }

    public Mono<ShellyProRelayResponse> getHeatingPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HEATING).build())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError, clientResponse -> Mono.error(
                    new BoilerException("Heating pump issue check IP: " + shellyConfig.getBoilerHost())
                )
            )
            .bodyToMono(ShellyProRelayResponse.class)
            .onErrorResume(Exception.class, ex -> Mono.error(new BoilerException(ex.getMessage())));
    }

    public Mono<Relay> controlFurnace(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, FURNACE)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError, clientResponse -> Mono.error(
                    new BoilerException("Furnace issue check IP: " + shellyConfig.getBoilerHost())
                )
            )
            .bodyToMono(Relay.class)
            .onErrorResume(Exception.class, ex -> Mono.error(new BoilerException(ex.getMessage())));
    }
}
