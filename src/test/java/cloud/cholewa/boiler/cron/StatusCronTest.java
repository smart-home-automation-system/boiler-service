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
}
