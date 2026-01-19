package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.home.model.SystemActiveReply;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
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
class HeatingPumpServiceTest {

    @Spy
    private BoilerConfig boilerConfig;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ShellyClient shellyClient;

    @InjectMocks
    private HeatingPumpService sut;

    @Test
    void should_not_control_pump_when_lastMessage_is_null() {
        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(boilerConfig.getHeating().getLastMessage().getMessage()).isEqualTo("Pump status updated");
    }

    @Test
    void should_enable_pump_without_fetching_current_status_when_pump_inactive_and_water_pump_inactive() {
        boilerConfig.getHeating().setLastMessage(newMessage());

        when(shellyClient.controlHeatingPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isTrue();
        assertThat(boilerConfig.getWater().isWorking()).isFalse();
    }

    @Test
    void should_enable_pump_fetching_current_status_when_pump_inactive_and_water_pump_inactive() {
        boilerConfig.getHeating().setLastMessage(oldMessage());
        boilerConfig.getHeating().setWorking(false);

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        when(shellyClient.controlHeatingPump(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isTrue();
        assertThat(boilerConfig.getWater().isWorking()).isFalse();
    }

    @Test
    void should_not_enable_pump_without_fetching_current_status_when_pump_inactive_and_water_pump_active() {
        boilerConfig.getHeating().setLastMessage(newMessage());
        boilerConfig.getHeating().setWorking(false);
        boilerConfig.getWater().setWorking(true);

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getHeatingPumpStatus();
        verify(shellyClient, never()).controlHeatingPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isTrue();
    }

    @Test
    void should_not_enable_pump_fetching_current_status_when_pump_inactive_and_water_pump_active() {
        boilerConfig.getHeating().setLastMessage(oldMessage());
        boilerConfig.getHeating().setWorking(false);
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getHeatingPumpStatus();
        verify(shellyClient, never()).controlHeatingPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isTrue();
    }

    @Test
    void should_disable_pump_without_fetching_current_status_when_pump_active() {
        boilerConfig.getHeating().setLastMessage(newMessage());
        boilerConfig.getHeating().setWorking(true);

        when(shellyClient.controlHeatingPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isFalse();
    }

    @Test
    void should_disable_pump_fetching_current_status_when_pump_active() {
        boilerConfig.getHeating().setLastMessage(oldMessage());
        boilerConfig.getHeating().setWorking(true);
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlHeatingPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isTrue();
    }

    @Test
    void should_not_control_pump_without_fetching_current_status_when_pump_has_expected_status() {
        boilerConfig.getHeating().setLastMessage(newMessage());
        boilerConfig.getHeating().setWorking(false);

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getHeatingPumpStatus();
        verify(shellyClient, never()).controlHeatingPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isFalse();
    }

    @Test
    void should_disable_pump_fetching_current_status_when_pump_has_expected_status_and_water_pump_active() {
        boilerConfig.getHeating().setLastMessage(oldMessage());
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlHeatingPump(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlPump(SystemActiveReply.builder().active(true).build())
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getHeating().isWorking()).isFalse();
        assertThat(boilerConfig.getWater().isWorking()).isTrue();
    }

    @Test
    void should_fetch_current_status_and_return_error_when_shelly_client_returns_error_fetching_current_status() {
        boilerConfig.getHeating().setLastMessage(oldMessage());

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.error(new RuntimeException("Error fetching status")));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, times(1)).getHeatingPumpStatus();

        verifyNoMoreInteractions(shellyClient);
    }

    @Test
    void should_fetch_current_status_and_return_error_when_shelly_client_returns_error_controlling_pump() {
        boilerConfig.getHeating().setLastMessage(oldMessage());

        when(shellyClient.getHeatingPumpStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlHeatingPump(anyBoolean()))
            .thenReturn(Mono.error(new RuntimeException("Error controlling pump")));

        sut.controlPump(SystemActiveReply.builder().active(false).build())
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, times(1)).getHeatingPumpStatus();
        verify(shellyClient, times(1)).controlHeatingPump(anyBoolean());

        verifyNoMoreInteractions(shellyClient);
    }

    private LastMessage newMessage() {
        LastMessage lastMessage = new LastMessage("test");
        lastMessage.setTimestamp(LocalDateTime.now());
        return lastMessage;
    }

    private LastMessage oldMessage() {
        LastMessage lastMessage = new LastMessage("test");
        lastMessage.setTimestamp(LocalDateTime.now().minusMinutes(3));
        return lastMessage;
    }
}
