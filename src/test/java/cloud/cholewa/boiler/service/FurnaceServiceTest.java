package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.client.ShellyClient;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.LastMessage;
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
class FurnaceServiceTest {

    @Spy
    private BoilerConfig boilerConfig;
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ShellyClient shellyClient;

    @InjectMocks
    private FurnaceService sut;

    @Test
    void should_control_furnace_when_last_message_is_null() {
        boilerConfig.getFurnace().setLastMessage(null);

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlFurnace(anyBoolean()))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(false);

        verifyNoMoreInteractions(shellyClient);
    }

    @Test
    void should_not_fetch_status_and_enable_furnace_when_heating_pum_working() {
        boilerConfig.getHeating().setWorking(true);
        boilerConfig.getFurnace().setLastMessage(newMessage());
        boilerConfig.getFurnace().setWorking(false);

        when(shellyClient.controlFurnace(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_not_fetch_status_and_enable_furnace_when_water_pum_working() {
        boilerConfig.getFurnace().setLastMessage(newMessage());
        boilerConfig.getFurnace().setWorking(false);
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.controlFurnace(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_fetch_status_and_enable_furnace_when_heating_pum_working() {
        boilerConfig.getFurnace().setLastMessage(oldMessage());
        boilerConfig.getFurnace().setWorking(false);
        boilerConfig.getHeating().setWorking(true);

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        when(shellyClient.controlFurnace(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_fetch_status_and_enable_furnace_when_water_pum_working() {
        boilerConfig.getFurnace().setLastMessage(oldMessage());
        boilerConfig.getFurnace().setWorking(false);
        boilerConfig.getWater().setWorking(true);

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        when(shellyClient.controlFurnace(true))
            .thenReturn(Mono.just(Relay.builder().ison(true).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(true);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_not_fetch_status_and_disable_furnace_when_no_pum_working() {
        boilerConfig.getFurnace().setLastMessage(newMessage());
        boilerConfig.getFurnace().setWorking(true);

        when(shellyClient.controlFurnace(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isFalse();
    }

    @Test
    void should_fetch_status_and_disable_furnace_when_no_pum_working() {
        boilerConfig.getFurnace().setLastMessage(oldMessage());
        boilerConfig.getFurnace().setWorking(true);

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(true).build()));

        when(shellyClient.controlFurnace(false))
            .thenReturn(Mono.just(Relay.builder().ison(false).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(false);

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isFalse();
    }

    @Test
    void should_not_fetch_status_and_not_control_furnace_when_any_pum_working_and_furnace_is_active() {
        boilerConfig.getFurnace().setLastMessage(newMessage());
        boilerConfig.getFurnace().setWorking(true);
        boilerConfig.getHeating().setWorking(true);

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, never()).getFurnaceStatus();
        verify(shellyClient, never()).controlFurnace(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_fetch_status_and_not_control_furnace_when_no_pum_working_and_furnace_is_inactive() {
        boilerConfig.getFurnace().setLastMessage(oldMessage());

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.just(ShellyProRelayResponse.builder().ison(false).build()));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyComplete();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, never()).controlFurnace(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isFalse();
    }

    @Test
    void should_throw_exception_when_fetch_status_fail() {
        boilerConfig.getFurnace().setLastMessage(oldMessage());
        boilerConfig.getFurnace().setWorking(true);

        when(shellyClient.getFurnaceStatus())
            .thenReturn(Mono.error(new RuntimeException("Test exception")));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, times(1)).getFurnaceStatus();
        verify(shellyClient, never()).controlFurnace(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    @Test
    void should_throw_exception_when_control_furnace_fail() {
        boilerConfig.getFurnace().setLastMessage(newMessage());
        boilerConfig.getFurnace().setWorking(true);

        when(shellyClient.controlFurnace(false))
            .thenReturn(Mono.error(new RuntimeException("Test exception")));

        sut.controlFurnace()
            .as(StepVerifier::create)
            .verifyError();

        verify(shellyClient, never()).getFurnaceStatus();
        verify(shellyClient, times(1)).controlFurnace(anyBoolean());

        verifyNoMoreInteractions(shellyClient);

        assertThat(boilerConfig.getFurnace().isWorking()).isTrue();
    }

    private LastMessage oldMessage() {
        LastMessage lastMessage = new LastMessage("test message");
        lastMessage.setTimestamp(LocalDateTime.now().minusMinutes(3));
        return lastMessage;
    }

    private LastMessage newMessage() {
        LastMessage lastMessage = new LastMessage("test message");
        lastMessage.setTimestamp(LocalDateTime.now().minusSeconds(40));
        return lastMessage;
    }
}
