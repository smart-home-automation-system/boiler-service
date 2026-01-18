package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.DeviceStatus;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.home.model.SystemActiveReply;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterPumpServiceTest {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private BoilerConfig boilerConfig;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ShellyClient shellyClient;

    @InjectMocks
    private WaterPumpService sut;

    @Test
    void should_enable_pump_without_fetching_current_status_when_pump_inactive() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(false).lastMessage(getNewMessage()).build());

        when(shellyClient.controlWaterPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(4)).getWater();
        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(true);

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_enable_pump_with_fetching_current_status_when_pump_inactive() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(false).lastMessage(getOldMessage()).build());

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        when(shellyClient.controlWaterPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(6)).getWater();
        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(true);

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_disable_pump_without_fetching_current_status_when_pump_active() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(true).lastMessage(getNewMessage()).build());

        when(shellyClient.controlWaterPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(4)).getWater();
        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(false);

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_disable_pump_with_fetching_current_status_when_pump_active() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(true).lastMessage(getOldMessage()).build());

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlWaterPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(6)).getWater();
        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(false);

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_not_control_pump_without_fetching_current_status_when_pump_has_expected_status() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(false).lastMessage(getNewMessage()).build());

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(2)).getWater();
        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_not_control_pump_with_fetching_current_status_when_pump_has_expected_status() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(true).lastMessage(getNewMessage()).build());

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(boilerConfig, times(2)).getWater();
        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_not_control_pump_when_shelly_client_returns_error_when_fetching_current_status() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(false).lastMessage(getOldMessage()).build());

        when(shellyClient.getWaterPumpStatus()).thenReturn(Mono.error(new RuntimeException()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(boilerConfig, times(1)).getWater();
        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    @Test
    void should_fetch_current_status_and_not_control_pump_when_shelly_client_returns_error_when_controlling_pump() {
        when(boilerConfig.getWater())
            .thenReturn(DeviceStatus.builder().isWorking(false).lastMessage(getOldMessage()).build());

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        when(shellyClient.controlWaterPump(true)).thenReturn(Mono.error(new RuntimeException()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(boilerConfig, times(4)).getWater();
        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient, boilerConfig);
    }

    private LastMessage getOldMessage() {
        LastMessage lastMessage = new LastMessage("test message");
        lastMessage.setTimestamp(LocalDateTime.now().minusMinutes(3));
        return lastMessage;
    }

    private LastMessage getNewMessage() {
        LastMessage lastMessage = new LastMessage("test message");
        lastMessage.setTimestamp(LocalDateTime.now());
        return lastMessage;
    }
}
