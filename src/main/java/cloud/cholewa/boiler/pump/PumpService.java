package cloud.cholewa.boiler.pump;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.DeviceRabbitMessage;
import cloud.cholewa.boiler.model.DeviceStatus;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.boiler.shelly.ShellyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PumpService {

    private final BoilerConfig boiler;

    private final ShellyClient shellyClient;

//    @RabbitListener(queues = "pumps")
//    void handlePumps(final DeviceRabbitMessage body) {
//
//        switch (DeviceType.valueOf(body.getName().toUpperCase())) {
//            case CIRCULATION -> handleCirculationPump(body);
//            case HOT_WATER -> handleHotWaterPump(body);
//            case HEATING -> handleHeatingPump(body);
//            case FLOOR -> handleFloorPump(body);
//            default -> throw new BoilerException("Unknown device type {}" + body);
//        }
//    }

    private void handleCirculationPump(final DeviceRabbitMessage body) {
        logMessageDetails(body);

        Mono.just(boiler.getCirculation())
            .doOnNext(circulation -> updateLastMessage(circulation, body))
            .zipWith(shellyClient.controlCirculationPump(body.isEnabled()))
            .map(t -> {
                t.getT1().setWorking(Boolean.TRUE.equals(t.getT2().getIson()));
                return t.getT1();
            })
            .delayElement(Duration.ofMinutes(1))
            .doOnNext(circulation -> circulation.setWorking(false))
            .subscribe(
                circulation -> {
                },
                error -> log.error("Errors handling circulation pump: {}", error.getMessage())
            );
    }

    private void handleHotWaterPump(final DeviceRabbitMessage body) {
        logMessageDetails(body);

        Mono.just(boiler.getHotWater())
            .doOnNext(hotWater -> updateLastMessage(hotWater, body))
            .flatMap(this::optionallyDisableHeatingPump)
            .zipWith(shellyClient.controlHotWaterPump(body.isEnabled()))
            .map(t -> {
                t.getT1().setWorking(Boolean.TRUE.equals(t.getT2().getIson()));
                return t.getT1();
            })
            .flatMap(this::optionallyEnableHeatingPump)
            .subscribe(
                hotWater -> {
                },
                error -> log.error("Errors handling hotWater pump: {}", error.getMessage())
            );
    }

    private Mono<DeviceStatus> optionallyDisableHeatingPump(final DeviceStatus deviceStatus) {
        return shellyClient.getHeatingPumpStatus()
            .flatMap(status -> {
                if (Boolean.TRUE.equals(status.getIson())) {
                    log.info("Heating pump stopped due higher priority of Hot Water pump");
                    return shellyClient.controlHeatingPump(false).then(Mono.just(deviceStatus));
                }
                return Mono.just(deviceStatus);
            });
    }

    private Mono<DeviceStatus> optionallyEnableHeatingPump(final DeviceStatus deviceStatus) {
        if (boiler.getHeating().isWorking()) {
            return shellyClient.controlHeatingPump(true)
                .doOnNext(relay -> log.info("Heating pump started after stopping Hot Water pump"))
                .then(Mono.just(deviceStatus));
        }
        return Mono.just(deviceStatus);
    }

    private void handleHeatingPump(final DeviceRabbitMessage body) {
        logMessageDetails(body);

        Mono.just(boiler.getHeating())
            .doOnNext(heating -> updateLastMessage(heating, body))
            .flatMap(this::checkPermissionsToEnableHeatingPump)
            .zipWith(shellyClient.controlHeatingPump(body.isEnabled()))
            .map(t -> {
                t.getT1().setWorking(Boolean.TRUE.equals(t.getT2().getIson()));
                return t.getT1();
            })
            .subscribe(
                heating -> {
                },
                error -> log.error("Errors handling heating pump: {}", error.getMessage())
            );
    }

    private Mono<DeviceStatus> checkPermissionsToEnableHeatingPump(final DeviceStatus deviceStatus) {
        return shellyClient.getHotWaterPumpStatus()
            .flatMap(status -> {
                if (Boolean.TRUE.equals(status.getIson())) {
                    log.info("Starting Heating pump is not permitted, due Hot Water pump is working");
                    return Mono.empty();
                }
                return Mono.just(deviceStatus);
            });
    }

    private void logMessageDetails(DeviceRabbitMessage body) {
        log.info("Incoming device message received pump={}, enabled:{}", body.getName(), body.isEnabled());
    }

    private void updateLastMessage(final DeviceStatus device, final DeviceRabbitMessage body) {
        device.setLastMessage(LastMessage.builder()
            .timestamp(LocalDateTime.now())
            .message(body.toString())
            .build());
    }
}
