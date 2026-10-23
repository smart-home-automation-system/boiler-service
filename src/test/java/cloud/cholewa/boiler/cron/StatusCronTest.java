package cloud.cholewa.boiler.cron;

import cloud.cholewa.boiler.client.HeatingClient;
import cloud.cholewa.boiler.client.WaterClient;
import cloud.cholewa.boiler.service.BoilerService;
import cloud.cholewa.boiler.service.ShellyAvailabilityMonitor;
import cloud.cholewa.home.model.SystemActiveReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusCronTest {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private HeatingClient heatingClient;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private WaterClient waterClient;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private BoilerService boilerService;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ShellyAvailabilityMonitor shellyAvailabilityMonitor;

    @InjectMocks
    private StatusCron sut;

    @Test
    void should_update_status() {
        when(waterClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(true).build()));
        when(heatingClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(false).build()));
        when(boilerService.controlBoilerDevices(any(), any())).thenReturn(Mono.empty());
        when(shellyAvailabilityMonitor.report()).thenReturn(Mono.empty());

        sut.updateStatus()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(waterClient, times(1)).querySystemActive();
        verify(heatingClient, times(1)).querySystemActive();
        verify(boilerService, times(1)).controlBoilerDevices(any(), any());
    }

    @Test
    void should_report_the_availability_of_the_device_when_the_pass_itself_failed() {
        final PublisherProbe<Void> report = PublisherProbe.empty();
        when(waterClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(true).build()));
        when(heatingClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(false).build()));
        when(boilerService.controlBoilerDevices(any(), any())).thenReturn(Mono.error(new IllegalStateException("broken")));
        when(shellyAvailabilityMonitor.report()).thenReturn(report.mono());

        sut.updateStatus()
            .as(StepVerifier::create)
            .verifyComplete();

        report.assertWasSubscribed();
    }

    //the monitor is asked once the devices were driven: only then has it heard how the calls of
    //this pass ended, and an alert about the device must not hold up its control
    @Test
    void should_report_the_availability_of_the_device_after_the_devices_were_controlled() {
        final PublisherProbe<Void> control = PublisherProbe.empty();
        final PublisherProbe<Void> report = PublisherProbe.empty();
        when(waterClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(true).build()));
        when(heatingClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(false).build()));
        when(boilerService.controlBoilerDevices(any(), any()))
            .thenReturn(control.mono().doOnSubscribe(subscription -> report.assertWasNotSubscribed()));
        when(shellyAvailabilityMonitor.report())
            .thenReturn(report.mono().doOnSubscribe(subscription -> control.assertWasSubscribed()));

        sut.updateStatus()
            .as(StepVerifier::create)
            .verifyComplete();

        control.assertWasSubscribed();
        report.assertWasSubscribed();
    }
    //HAS-306: a pass that never ends - a call hung on a dead connection - ends at its bound, and the next
    //one runs: the scheduler is not held for good. The monitor still closes its pass
    @Test
    void should_end_a_pass_that_never_completes_at_its_bound() {
        when(waterClient.querySystemActive()).thenReturn(Mono.never());
        when(heatingClient.querySystemActive()).thenReturn(Mono.never());
        final PublisherProbe<Void> report = PublisherProbe.empty();
        when(shellyAvailabilityMonitor.report()).thenReturn(report.mono());

        StepVerifier.withVirtualTime(sut::updateStatus)
            .expectSubscription()
            .expectNoEvent(StatusCron.LIMIT.minusSeconds(1))
            .thenAwait(Duration.ofSeconds(1))
            .verifyComplete();

        report.assertWasSubscribed();
    }

    //Spring calls the method once and subscribes to the same Mono for every run: the pass is built anew
    @Test
    void should_build_the_pass_anew_for_every_run() {
        when(waterClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(true).build()));
        when(heatingClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(false).build()));
        when(boilerService.controlBoilerDevices(any(), any())).thenReturn(Mono.empty());
        final PublisherProbe<Void> report = PublisherProbe.empty();
        when(shellyAvailabilityMonitor.report()).thenReturn(report.mono());

        final Mono<Void> scheduled = sut.updateStatus();
        scheduled.as(StepVerifier::create).verifyComplete();
        scheduled.as(StepVerifier::create).verifyComplete();

        verify(waterClient, times(2)).querySystemActive();
        //the report is deferred in the monitor: one Mono, subscribed by every run
        assertThat(report.subscribeCount()).isEqualTo(2);
    }
}
