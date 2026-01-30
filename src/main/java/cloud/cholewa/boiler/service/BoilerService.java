package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.home.model.SystemActiveReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoilerService {

    private final BoilerConfig boilerConfig;
    private final BoilerStatusMapper boilerStatusMapper;
    private final WaterPumpService waterPumpService;
    private final HeatingPumpService heatingPumpService;
    private final FurnaceService furnaceService;

    public Mono<BoilerStatusReply> getBoilerStatus() {
        return Mono.fromCallable(() -> boilerStatusMapper.toBoilerStatusReply(boilerConfig))
            .doOnSubscribe(subscription -> log.info("Received request for boiler status"));
    }

    public Mono<Void> controlBoilerDevices(
        final SystemActiveReply waterSystemActiveReply,
        final SystemActiveReply heatingSystemActiveReply
    ) {
        return waterPumpService.controlPump(waterSystemActiveReply)
            .then(heatingPumpService.controlPump(heatingSystemActiveReply))
            .then(furnaceService.controlFurnace());
    }
}
