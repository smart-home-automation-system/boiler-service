package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.home.model.SystemActiveReply;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyPro4StatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterPumpServiceTest {

    @Spy
    private BoilerConfig boilerConfig;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ShellyClient shellyClient;

    @InjectMocks
    private WaterPumpService sut;

    @Test
    void should_not_control_pump_when_lastMessage_is_null() {
        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyPro4StatusResponse.builder().output(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(boilerConfig.getWater().getLastMessage().getMessage()).isEqualTo("Pump status updated");
    }

    @Test
    void should_enable_pump_without_fetching_current_status_when_pump_inactive() {
        boilerConfig.getWater().setLastMessage(getNewMessage());
        boilerConfig.getWater().setWorking(false);
        boilerConfig.getHeating().setWorking(true);

        when(shellyClient.controlWaterPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        when(shellyClient.controlHeatingPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(true);
        verify(shellyClient, times(1)).controlHeatingPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isTrue();
        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
    }

    @Test
    void should_enable_pump_with_fetching_current_status_when_pump_inactive() {
        boilerConfig.getWater().setWorking(false);

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyPro4StatusResponse.builder().output(false).build()));

        when(shellyClient.controlWaterPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isTrue();
        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
    }

    @Test
    void should_disable_pump_without_fetching_current_status_when_pump_active() {
        boilerConfig.getWater().setLastMessage(getNewMessage());
        boilerConfig.getWater().setWorking(true);
        boilerConfig.getHeating().setWorking(true);

        when(shellyClient.controlWaterPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isFalse();
        assertThat(boilerConfig.getHeating().isWorking()).isTrue();
    }

    @Test
    void should_disable_pump_with_fetching_current_status_when_pump_active() {
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyPro4StatusResponse.builder().output(true).build()));

        when(shellyClient.controlWaterPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isFalse();
        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
    }

    @Test
    void should_not_control_pump_without_fetching_current_status_when_pump_has_expected_status() {
        boilerConfig.getWater().setLastMessage(getNewMessage());
        boilerConfig.getWater().setWorking(false);
        boilerConfig.getHeating().setWorking(true);

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isFalse();
        assertThat(boilerConfig.getHeating().isWorking()).isTrue();
    }

    @Test
    void should_not_control_pump_with_fetching_current_status_when_pump_has_expected_status() {
        boilerConfig.getWater().setLastMessage(getOldMessage());
        boilerConfig.getWater().setWorking(true);
        boilerConfig.getHeating().setWorking(true);

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyPro4StatusResponse.builder().output(true).build()));

        when(shellyClient.controlHeatingPump(anyBoolean()))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());
        verify(shellyClient, times(1)).controlHeatingPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getWater().isWorking()).isTrue();
        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
    }

    @Test
    void should_not_control_pump_when_shelly_client_returns_error_when_fetching_current_status() {
        boilerConfig.getWater().setLastMessage(getOldMessage());

        when(shellyClient.getWaterPumpStatus()).thenReturn(Mono.error(new RuntimeException()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, never()).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);
    }

    @Test
    void should_fetch_current_status_and_not_control_pump_when_shelly_client_returns_error_when_controlling_pump() {
        boilerConfig.getWater().setLastMessage(getOldMessage());

        when(shellyClient.getWaterPumpStatus())
            .thenReturn(Mono.just(ShellyPro4StatusResponse.builder().output(false).build()));

        when(shellyClient.controlWaterPump(true)).thenReturn(Mono.error(new RuntimeException()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, times(1)).getWaterPumpStatus();
        verify(shellyClient, times(1)).controlWaterPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);
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
