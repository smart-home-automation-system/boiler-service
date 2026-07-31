package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.boiler.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.home.model.SystemActiveReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoilerServiceTest {

    private static final SystemActiveReply ACTIVE = SystemActiveReply.builder().active(true).build();

    @Mock
    private BoilerStatusMapper boilerStatusMapper;
    @Mock
    private WaterPumpService waterPumpService;
    @Mock
    private HeatingPumpService heatingPumpService;
    @Mock
    private FurnaceService furnaceService;

    @InjectMocks
    private BoilerService sut;

    @Test
    void should_return_boiler_status() {
        when(boilerStatusMapper.toBoilerStatusReply(any())).thenReturn(BoilerStatusReply.builder().build());

        sut.getBoilerStatus()
            .as(StepVerifier::create)
            .assertNext(reply ->
                assertThat(reply).isInstanceOf(BoilerStatusReply.class))
            .verifyComplete();
    }

    @Test
    void should_control_devices_in_order() {
        List<String> controlled = new ArrayList<>();
        when(waterPumpService.controlPump(any())).thenReturn(Mono.fromRunnable(() -> controlled.add("water")));
        when(heatingPumpService.controlPump(any())).thenReturn(Mono.fromRunnable(() -> controlled.add("heating")));
        when(furnaceService.controlFurnace()).thenReturn(Mono.fromRunnable(() -> controlled.add("furnace")));

        sut.controlBoilerDevices(ACTIVE, ACTIVE)
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(controlled).containsExactly("water", "heating", "furnace");
    }

    @Test
    void should_control_furnace_when_water_pump_fails() {
        List<String> controlled = new ArrayList<>();
        when(waterPumpService.controlPump(any())).thenReturn(Mono.error(new BoilerException("water pump down")));
        when(heatingPumpService.controlPump(any())).thenReturn(Mono.fromRunnable(() -> controlled.add("heating")));
        when(furnaceService.controlFurnace()).thenReturn(Mono.fromRunnable(() -> controlled.add("furnace")));

        sut.controlBoilerDevices(ACTIVE, ACTIVE)
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(controlled).containsExactly("heating", "furnace");
    }

    @Test
    void should_control_furnace_when_heating_pump_fails() {
        List<String> controlled = new ArrayList<>();
        when(waterPumpService.controlPump(any())).thenReturn(Mono.fromRunnable(() -> controlled.add("water")));
        when(heatingPumpService.controlPump(any())).thenReturn(Mono.error(new BoilerException("heating pump down")));
        when(furnaceService.controlFurnace()).thenReturn(Mono.fromRunnable(() -> controlled.add("furnace")));

        sut.controlBoilerDevices(ACTIVE, ACTIVE)
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(controlled).containsExactly("water", "furnace");
    }

    @Test
    void should_complete_when_furnace_fails() {
        when(waterPumpService.controlPump(any())).thenReturn(Mono.empty());
        when(heatingPumpService.controlPump(any())).thenReturn(Mono.empty());
        when(furnaceService.controlFurnace()).thenReturn(Mono.error(new BoilerException("furnace down")));

        sut.controlBoilerDevices(ACTIVE, ACTIVE)
            .as(StepVerifier::create)
            .verifyComplete();
    }
}
