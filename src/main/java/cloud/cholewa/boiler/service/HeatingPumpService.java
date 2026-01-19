package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.home.model.SystemActiveReply;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatingPumpService {

    private final BoilerConfig boilerConfig;
    private final ShellyClient shellyClient;

    public Mono<Void> controlPump(final SystemActiveReply heatingSystemActiveReply) {
        return updatePumpStatus()
            .then(setPumpState(heatingSystemActiveReply));
    }

    private Mono<Void> updatePumpStatus() {
        return Mono.fromCallable(this::getLastMessage)
            .filter(this::notUpdatedWithinLastMinute)
            .doOnNext(lastMessage -> log.info("Querying heating pump status"))
            .flatMap(lastMessage -> shellyClient.getHeatingPumpStatus())
            .doOnNext(this::updateBoilerConfig)
            .then();
    }

    private LastMessage getLastMessage() {
        LastMessage lastMessage = new LastMessage("No status update available");
        lastMessage.setTimestamp(LocalDateTime.MIN);

        return boilerConfig.getHeating().getLastMessage() == null
            ? lastMessage
            : boilerConfig.getHeating().getLastMessage();
    }

    private boolean notUpdatedWithinLastMinute(final LastMessage lastMessage) {
        return lastMessage.getTimestamp().isBefore(LocalDateTime.now().minusMinutes(1));
    }

    private void updateBoilerConfig(final ShellyProRelayResponse shellyProRelayResponse) {
        boilerConfig.getHeating().setWorking(Boolean.TRUE.equals(shellyProRelayResponse.getIson()));
        boilerConfig.getHeating().setLastMessage(new LastMessage("Pump status updated"));
    }

    private Mono<Void> setPumpState(final SystemActiveReply heatingSystemActiveReply) {
        return Mono.just(heatingSystemActiveReply)
            .flatMap(reply -> {
                if (boilerConfig.getWater().isWorking() && boilerConfig.getHeating().isWorking()) {
                    log.info("Both pumps are active, disabling heating pump");
                    return shellyClient.controlHeatingPump(false);
                } else if (reply.getActive() == boilerConfig.getHeating().isWorking()) {
                    log.info("Pump state unchanged");
                    return Mono.empty();
                } else {
                    return allowDisablePumpEvenIfWaterPumpIsWorking(reply);
                }
            })
            .doOnNext(relay -> {
                log.info("Pump state changed to: {}", relay.getIson());
                boilerConfig.getHeating().setWorking(Boolean.TRUE.equals(relay.getIson()));
                boilerConfig.getHeating().setLastMessage(new LastMessage("Pump state changed to: " + relay.getIson()));
            })
            .then();
    }

    private Mono<Relay> allowDisablePumpEvenIfWaterPumpIsWorking(final SystemActiveReply reply) {
        if (boilerConfig.getWater().isWorking()) {
            if (reply.getActive()) {
                log.info("Water pump is active, enabling pump is not allowed");
                return Mono.empty();
            }
            return shellyClient.controlHeatingPump(false);
        } else {
            return shellyClient.controlHeatingPump(reply.getActive());
        }
    }
}
