package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.shelly.model.ShellyPro4StatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class FurnaceService {

    private final BoilerConfig boilerConfig;
    private final ShellyClient shellyClient;

    public Mono<Void> controlFurnace() {
        return updateFurnaceStatus()
            .then(setFurnaceState());
    }

    private Mono<Void> updateFurnaceStatus() {
        return Mono.fromCallable(this::getLastMessage)
            .filter(this::notUpdatedWithinLastMinute)
            .doOnNext(lastMessage -> log.info("Querying furnace status"))
            .flatMap(lastMessage -> shellyClient.getFurnaceStatus())
            .doOnNext(this::updateFurnaceConfig)
            .then();
    }

    private LastMessage getLastMessage() {
        LastMessage lastMessage = new LastMessage("No status update available");
        lastMessage.setTimestamp(LocalDateTime.MIN);

        return boilerConfig.getFurnace().getLastMessage() == null
            ? lastMessage
            : boilerConfig.getFurnace().getLastMessage();
    }

    private boolean notUpdatedWithinLastMinute(final LastMessage lastMessage) {
        return lastMessage.getTimestamp().isBefore(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(1));
    }

    private void updateFurnaceConfig(final ShellyPro4StatusResponse response) {
        boilerConfig.getFurnace().setWorking(Boolean.TRUE.equals(response.getOutput()));
        boilerConfig.getFurnace().setLastMessage(new LastMessage("Furnace status updated"));
    }

    private Mono<Void> setFurnaceState() {
        return Mono.defer(() ->
            boilerConfig.getHeating().isWorking() || boilerConfig.getWater().isWorking()
                ? enableFurnace()
                : disableFurnace()
        );
    }

    private Mono<Void> enableFurnace() {
        return Mono.just(boilerConfig.getFurnace().isWorking())
            .filter(working -> !working)
            .flatMap(working -> shellyClient.controlFurnace(true))
            .doOnNext(relay -> {
                log.info("Enabling furnace");
                boilerConfig.getFurnace().setWorking(Boolean.TRUE.equals(relay.getIson()));
                boilerConfig.getFurnace().setLastMessage(new LastMessage("Furnace state changed to: " + relay.getIson()));
            })
            .switchIfEmpty(Mono.fromRunnable(() -> log.info("Furnace is already enabled")))
            .then();
    }

    private Mono<Void> disableFurnace() {
        return Mono.just(boilerConfig.getFurnace().isWorking())
            .filter(working -> working)
            .flatMap(working -> shellyClient.controlFurnace(false))
            .doOnNext(relay -> {
                log.info("Disabling furnace");
                boilerConfig.getFurnace().setWorking(Boolean.TRUE.equals(relay.getIson()));
                boilerConfig.getFurnace().setLastMessage(new LastMessage("Furnace state changed to: " + relay.getIson()));
            })
            .switchIfEmpty(Mono.fromRunnable(() -> log.info("Furnace is already disabled")))
            .then();
    }
}
