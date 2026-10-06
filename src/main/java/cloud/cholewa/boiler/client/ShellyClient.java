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
    private final ShellyCallListener callListener;

    public Mono<ShellyPro4StatusResponse> getWaterPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HOT_WATER).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, "Error fetching water pump status"));
    }

    public Mono<Relay> controlWaterPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HOT_WATER)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, "Error controlling water pump"));
    }

    public Mono<ShellyPro4StatusResponse> getHeatingPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HEATING).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, "Error fetching heating pump status"));
    }

    public Mono<Relay> controlHeatingPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HEATING)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, "Error controlling heating pump"));
    }

    public Mono<ShellyPro4StatusResponse> getFurnaceStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, FURNACE).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, "Error fetching furnace status"));
    }

    public Mono<Relay> controlFurnace(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, FURNACE)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, "Error controlling furnace"));
    }

    //what every call to the device has in common: the listener hears about its outcome - all six
    //go to the one Shelly, so any answer says it works and any failure that it may not - and a
    //failure leaves as a BoilerException with a fixed text, the cause staying in the log
    private <T> Mono<T> watched(final Mono<T> call, final String failure) {
        return call
            //a 2xx without a body completes without a value: neither an answer nor an error, so
            //nobody would ever hear that the device stopped saying anything
            .switchIfEmpty(Mono.error(() -> new IllegalStateException("The device answered without a body")))
            .doOnNext(answer -> callListener.recordAnswer())
            .doOnError(throwable -> {
                log.error(failure, throwable);
                callListener.recordFailure();
            })
            .onErrorMap(throwable -> new BoilerException(failure));
    }
}
