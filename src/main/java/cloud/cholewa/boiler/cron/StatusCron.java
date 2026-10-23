package cloud.cholewa.boiler.cron;

import cloud.cholewa.boiler.client.HeatingClient;
import cloud.cholewa.boiler.client.WaterClient;
import cloud.cholewa.boiler.service.BoilerService;
import cloud.cholewa.boiler.service.ShellyAvailabilityMonitor;
import cloud.cholewa.commons.scheduling.ScheduledPass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusCron {

    //Bounded (HAS-306): a pass that never ends - a call hung on a dead connection - held the one thread of
    //the scheduler for good in blinds-service on 2026-10-22, and here it would leave the furnace as it is.
    //The bound is above the fixed delay on purpose: a pass may legitimately take about two minutes (the
    //water and heating flags at 15 s, six calls of the Shelly at 15 s each - connect and response - and
    //30 s for a notification), and a pass cut in the middle of driving the furnace and the pumps is worse
    //than a late one. fixedDelay never lets two passes overlap, so the bound only ends one that will never
    //end on its own; the next pass reads the relays again
    static final Duration LIMIT = Duration.ofMinutes(3);

    private final HeatingClient heatingClient;
    private final WaterClient waterClient;
    private final BoilerService boilerService;
    private final ShellyAvailabilityMonitor shellyAvailabilityMonitor;

    //The report closes the pass of the monitor even when the bound cut the pass: inside the bound it would
    //be cancelled with it, and the calls of the cut pass would be counted into the next one. It is bounded
    //on its own (30 s) and signals no error
    @Scheduled(fixedDelayString = "PT1m", initialDelayString = "PT10s")
    public Mono<Void> updateStatus() {
        return ScheduledPass.bounded("boiler control pass", LIMIT, this::controlPass)
            //after the devices, never before: by now the monitor has heard how every call of this
            //pass ended. It reads the time and its state at subscription and signals no error
            .then(shellyAvailabilityMonitor.report());
    }

    private Mono<Void> controlPass() {
        return waterClient.querySystemActive()
            .zipWith(heatingClient.querySystemActive())
            .doOnNext(tuple ->
                log.info("Received status: water={}, heating={}", tuple.getT1().getActive(), tuple.getT2().getActive()))
            .flatMap(tuple ->
                boilerService.controlBoilerDevices(tuple.getT1(), tuple.getT2()))
            //every step of the pass handles its own failures; should one escape all the same, it
            //must not take the report below with it - a device that is gone would go unannounced
            //for as long as the error repeats
            .onErrorResume(throwable -> {
                log.error("Control pass failed", throwable);
                return Mono.empty();
            })
            .then();
    }
}
