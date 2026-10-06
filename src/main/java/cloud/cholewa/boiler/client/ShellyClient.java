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

import java.util.function.Predicate;

import static cloud.cholewa.boiler.model.BoilerDeviceType.FURNACE;
import static cloud.cholewa.boiler.model.BoilerDeviceType.HEATING;
import static cloud.cholewa.boiler.model.BoilerDeviceType.HOT_WATER;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellyClient {

    //the one field each answer is read for. JSON of any other shape decodes all the same, into
    //an object of nulls - "{}" from a portal or another device on the address - and would pass
    //for a relay that is off
    private static final Predicate<ShellyPro4StatusResponse> STATUS = status -> status.getOutput() != null;
    private static final Predicate<Relay> COMMAND = relay -> relay.getIson() != null;

    private final ShellyConfig shellyConfig;
    private final WebClient shellyWebClient;
    private final ShellyCallListener callListener;

    public Mono<ShellyPro4StatusResponse> getWaterPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HOT_WATER).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, STATUS, "Error fetching water pump status"));
    }

    public Mono<Relay> controlWaterPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HOT_WATER)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, COMMAND, "Error controlling water pump"));
    }

    public Mono<ShellyPro4StatusResponse> getHeatingPumpStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, HEATING).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, STATUS, "Error fetching heating pump status"));
    }

    public Mono<Relay> controlHeatingPump(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, HEATING)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, COMMAND, "Error controlling heating pump"));
    }

    public Mono<ShellyPro4StatusResponse> getFurnaceStatus() {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getStatusUriBuilder(uriBuilder, FURNACE).build())
            .retrieve()
            .bodyToMono(ShellyPro4StatusResponse.class)
            .transform(call -> watched(call, STATUS, "Error fetching furnace status"));
    }

    public Mono<Relay> controlFurnace(final boolean enable) {
        return shellyWebClient
            .get()
            .uri(uriBuilder -> shellyConfig.getControlUriBuilder(uriBuilder, FURNACE)
                .queryParam("turn", enable ? "on" : "off")
                .build())
            .retrieve()
            .bodyToMono(Relay.class)
            .transform(call -> watched(call, COMMAND, "Error controlling furnace"));
    }

    //what every call to the device has in common: the listener hears about its outcome - all six
    //go to the one Shelly, so any answer says it works and any failure that it may not - and a
    //failure leaves as a BoilerException with a fixed text, the cause staying in the log
    private <T> Mono<T> watched(final Mono<T> call, final Predicate<T> isAnswer, final String failure) {
        return call
            .filter(isAnswer)
            //a 2xx without a body completes without a value, and so does one filtered out above:
            //neither an answer nor an error, so nobody would ever hear that the device stopped
            //saying anything of use
            .switchIfEmpty(Mono.error(() -> new IllegalStateException("Not the answer of a Shelly")))
            .doOnNext(answer -> callListener.recordAnswer())
            .doOnError(throwable -> {
                log.error(failure, throwable);
                callListener.recordFailure();
            })
            .onErrorMap(throwable -> new BoilerException(failure));
    }
}
