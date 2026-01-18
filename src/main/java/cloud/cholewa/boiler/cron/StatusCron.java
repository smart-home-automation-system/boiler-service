package cloud.cholewa.boiler.cron;

import cloud.cholewa.boiler.client.HeatingClient;
import cloud.cholewa.boiler.client.WaterClient;
import cloud.cholewa.boiler.service.BoilerService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class StatusCron {

    private final HeatingClient heatingClient;
    private final WaterClient waterClient;
    private final BoilerService boilerService;

    @Scheduled(fixedRateString = "PT1m", initialDelayString = "PT10s")
    void updateStatus() {
        waterClient.querySystemActive()
            .zipWith(heatingClient.querySystemActive())
            .flatMap(tuple ->
                boilerService.controlBoilerDevices(tuple.getT1(), tuple.getT2()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }
}
