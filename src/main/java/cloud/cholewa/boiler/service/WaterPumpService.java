package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.home.model.SystemActiveReply;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterPumpService {

    private final BoilerConfig boilerConfig;
    private final ShellyClient shellyClient;

    public Mono<Void> controlPump(final SystemActiveReply waterSystemActiveReply) {
        return updatePumpStatus()
            .then(setPumpState(waterSystemActiveReply))
            .then(optionallyDisableHeatingPump());
    }

    private Mono<Void> updatePumpStatus() {
        return Mono.fromCallable(this::getLastMessage)
            .filter(this::notUpdatedWithinLastMinute)
            .doOnNext(lastMessage -> log.info("Querying water pump status"))
            .flatMap(lastMessage -> shellyClient.getWaterPumpStatus())
            .doOnNext(this::updateBoilerConfig)
            .then();
    }

    private LastMessage getLastMessage() {
        LastMessage lastMessage = new LastMessage("No status update available");
        lastMessage.setTimestamp(LocalDateTime.MIN);

        return boilerConfig.getWater().getLastMessage() == null
            ? lastMessage
            : boilerConfig.getWater().getLastMessage();
    }

    private boolean notUpdatedWithinLastMinute(final LastMessage lastMessage) {
        return lastMessage.getTimestamp().isBefore(LocalDateTime.now().minusMinutes(1));
    }

    private void updateBoilerConfig(final ShellyProRelayResponse shellyProRelayResponse) {
        boilerConfig.getWater().setWorking(Boolean.TRUE.equals(shellyProRelayResponse.getIson()));
        boilerConfig.getWater().setLastMessage(new LastMessage("Pump status updated"));
    }

    private Mono<Void> setPumpState(final SystemActiveReply waterSystemActiveReply) {
        return Mono.just(waterSystemActiveReply)
            .flatMap(reply -> {
                if (reply.getActive() == boilerConfig.getWater().isWorking()) {
                    log.info("Pump state unchanged");
                    return Mono.empty();
                } else {
                    return shellyClient.controlWaterPump(reply.getActive());
                }
            })
            .doOnNext(relay -> {
                log.info("Pump state changed to: {}", relay.getIson());
                boilerConfig.getWater().setWorking(Boolean.TRUE.equals(relay.getIson()));
                boilerConfig.getWater().setLastMessage(new LastMessage("Pump state changed to: " + relay.getIson()));
            })
            .then();
    }

    private Mono<Void> optionallyDisableHeatingPump() {
        return Mono.defer(() -> {
            if (boilerConfig.getHeating().isWorking() && boilerConfig.getWater().isWorking()) {
                return shellyClient.controlHeatingPump(false)
                    .doOnNext(response -> {
                        log.info("Heating pump disabled due to water pump being active");
                        boilerConfig.getHeating().setWorking(Boolean.TRUE.equals(response.getIson()));
                        boilerConfig.getHeating().setLastMessage(new LastMessage(
                            "Heating pump disabled due to water pump being active"));
                    })
                    .then();
            }
            return Mono.empty();
        });
    }
}
