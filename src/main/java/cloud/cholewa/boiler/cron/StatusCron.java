package cloud.cholewa.boiler.cron;

import cloud.cholewa.boiler.client.HeatingClient;
import cloud.cholewa.boiler.client.WaterClient;
import cloud.cholewa.boiler.service.BoilerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class StatusCron {

    private final HeatingClient heatingClient;
    private final WaterClient waterClient;
    private final BoilerService boilerService;

    @Scheduled(fixedDelayString = "PT1m", initialDelayString = "PT10s")
    public Mono<Void> updateStatus() {
        return waterClient.querySystemActive()
            .zipWith(heatingClient.querySystemActive())
            .doOnNext(tuple ->
                log.info("Received status: water={}, heating={}", tuple.getT1().getActive(), tuple.getT2().getActive()))
            .flatMap(tuple ->
                boilerService.controlBoilerDevices(tuple.getT1(), tuple.getT2()));
    }
}
