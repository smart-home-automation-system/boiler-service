package cloud.cholewa.boiler.furnace;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.DeviceRabbitMessage;
import cloud.cholewa.boiler.model.DeviceStatus;
import cloud.cholewa.boiler.model.DeviceType;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.boiler.shelly.ShellyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static cloud.cholewa.boiler.model.DeviceType.FURNACE;

@Slf4j
@Component
@RequiredArgsConstructor
public class FurnaceService {

    private final BoilerConfig boiler;
    private final ShellyClient shellyClient;

    @RabbitListener(queues = "furnace")
    void test(final DeviceRabbitMessage body) {

        if (DeviceType.valueOf(body.getName().toUpperCase()).equals(FURNACE)) {
            log.info("Incoming device message received furnace, enabled:{}", body.isEnabled());

            Mono.just(boiler.getFurnace())
                .flatMap(this::canFurnaceStart)
                .doOnNext(furnace -> updateDeviceStatus(furnace, body))
                .zipWith(shellyClient.controlFurnace(body.isEnabled()))
                .map(t -> {
                    t.getT1().setWorking(Boolean.TRUE.equals(t.getT2().getIson()));
                    return t.getT1();
                })
                .subscribe(
                    furnace -> {
                    },
                    error -> log.error("Errors handling furnace: {}", error.getMessage())
                )
            ;
        }

    }

    private Mono<DeviceStatus> canFurnaceStart(final DeviceStatus furnaceStatus) {
        return Mono.just(furnaceStatus)
            .flatMap(status -> shellyClient.getHotWaterPumpStatus()
                .flatMap(response -> {
                    if (Boolean.TRUE.equals(response.getIson())) {
                        return Mono.just(status);
                    }
                    return Mono.empty();
                })
            )
            .flatMap(status -> shellyClient.getHeatingPumpStatus()
                .flatMap(response -> {
                    if (Boolean.TRUE.equals(response.getIson())) {
                        return Mono.just(status);
                    }
                    return Mono.empty();
                })
            );
    }

    private void updateDeviceStatus(final DeviceStatus device, final DeviceRabbitMessage body) {
        device.setLastMessage(LastMessage.builder()
            .timestamp(LocalDateTime.now())
            .message(body.toString())
            .build());
    }
}
