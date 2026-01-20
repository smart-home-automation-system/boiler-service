package cloud.cholewa.boiler.cron;

import cloud.cholewa.boiler.client.HeatingClient;
import cloud.cholewa.boiler.client.WaterClient;
import cloud.cholewa.boiler.service.BoilerService;
import cloud.cholewa.home.model.SystemActiveReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    @InjectMocks
    private StatusCron sut;

    @Test
    void should_update_status() {
        when(waterClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(true).build()));
        when(heatingClient.querySystemActive()).thenReturn(Mono.just(SystemActiveReply.builder().active(false).build()));
        when(boilerService.controlBoilerDevices(any(), any())).thenReturn(Mono.empty());

        sut.updateStatus()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(waterClient, times(1)).querySystemActive();
        verify(heatingClient, times(1)).querySystemActive();
        verify(boilerService, times(1)).controlBoilerDevices(any(), any());
    }
}
