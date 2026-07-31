package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.home.model.SystemActiveReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Function;

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
        return waterPumpService.controlPump(waterSystemActiveReply).onErrorResume(skip("hot water pump"))
            .then(heatingPumpService.controlPump(heatingSystemActiveReply).onErrorResume(skip("heating pump")))
            .then(furnaceService.controlFurnace().onErrorResume(skip("furnace")));
    }

    /**
     * Keeps a failing device from cancelling the rest of the pass - without this the furnace,
     * controlled last, would be skipped whenever a pump call failed and would keep burning until
     * the next successful pass. Device state is only ever written from an actual device response,
     * so a skipped step leaves the cached state untouched and the furnace cannot be enabled on a
     * pump that never confirmed it is running. The stale {@code lastMessage} timestamp makes the
     * next pass query the device again.
     */
    private Function<Throwable, Mono<Void>> skip(final String device) {
        return throwable -> {
            log.warn("Skipping {} in this pass: {}", device, throwable.getMessage());
            return Mono.empty();
        };
    }
}
