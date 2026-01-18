package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.DeviceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FurnaceService {

    private final BoilerConfig boiler;
    private final ShellyClient shellyClient;

    public Mono<Void> controlFurnace() {
        return Mono.empty();
    }

//    @RabbitListener(queues = "furnace")
//    void handleFurnace(final DeviceRabbitMessage body) {
//
//        if (DeviceType.valueOf(body.getName().toUpperCase()).equals(FURNACE)) {
//            log.info("Incoming device message received furnace, enabled:{}", body.isEnabled());
//
//            Mono.just(boiler.getFurnace())
//                .flatMap(furnace -> body.isEnabled() ? startFurnace(furnace) : stopFurnace(furnace))
//                .doOnNext(furnace -> updateLastMessage(furnace, body))
//                .subscribe(
//                    furnace -> {
//                    },
//                    error -> log.error("Errors handling furnace: {}", error.getMessage())
//                );
//        }
//    }

    private Mono<DeviceStatus> startFurnace(final DeviceStatus deviceStatus) {
        return shouldFurnaceStart(deviceStatus)
            .zipWith(shellyClient.controlFurnace(true))
            .map(t -> {
                t.getT1().setWorking(Boolean.TRUE.equals(t.getT2().getIson()));
                return t.getT1();
            });
    }

    private Mono<DeviceStatus> stopFurnace(final DeviceStatus deviceStatus) {
        return shellyClient.controlFurnace(false)
            .flatMap(reply -> {
                deviceStatus.setWorking(Boolean.TRUE.equals(reply.getIson()));
                return Mono.just(deviceStatus);
            });
    }

    private Mono<DeviceStatus> shouldFurnaceStart(final DeviceStatus furnaceStatus) {
        return Mono.zip(shellyClient.getWaterPumpStatus(), shellyClient.getHeatingPumpStatus())
            .map(t -> Boolean.TRUE.equals(t.getT1().getIson())
                || Boolean.TRUE.equals(t.getT2().getIson()))
            .filter(anyIsOn -> anyIsOn)
            .map(isOn -> furnaceStatus);
    }

//    private void updateLastMessage(final DeviceStatus device, final DeviceRabbitMessage body) {
//        device.setLastMessage(LastMessage.builder()
//            .timestamp(LocalDateTime.now())
//            .message(body.toString())
//            .build());
//    }
}
