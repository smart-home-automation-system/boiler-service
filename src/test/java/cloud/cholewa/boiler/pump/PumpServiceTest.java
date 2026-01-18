package cloud.cholewa.boiler.pump;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.boiler.model.DeviceRabbitMessage;
import cloud.cholewa.boiler.shelly.ShellyClient;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PumpServiceTest {

//    private PumpService sut;
//    private BoilerConfig boilerConfig;
//    @Mock
//    private ShellyClient shellyClient;
//
//    @BeforeEach
//    void setUp() {
//        boilerConfig = new BoilerConfig();
//
//        sut = new PumpService(boilerConfig, shellyClient);
//    }
//
//    @Test
//    void shouldThrowIllegalArgumentExceptionWhenInvalidDeviceType() {
//        DeviceRabbitMessage body = DeviceRabbitMessage.builder().name(("InvalidDeviceType")).build();
//
//        assertThatExceptionOfType(IllegalArgumentException.class)
//            .isThrownBy(() -> sut.handlePumps(body));
//    }
//
//    @Test
//    void shouldThrowIllegalArgumentExceptionWhen() {
//        DeviceRabbitMessage body = DeviceRabbitMessage.builder().name(("furnace")).build();
//
//        assertThatExceptionOfType(BoilerException.class)
//            .isThrownBy(() -> sut.handlePumps(body))
//            .withMessageContaining("Unknown device type");
//    }
//
//    @ParameterizedTest(name = "{0}")
//    @MethodSource("dataSourceForCirculationPumpOnly")
//    void shouldHandleOnlyCirculationPumps(
//        final String name,
//        final Relay circulationPumpResponse,
//        final DeviceRabbitMessage body,
//        final int numberCirculationPump,
//        final boolean expectedCirculationPumpStatus
//    ) {
//        when(shellyClient.controlCirculationPump(anyBoolean())).thenReturn(Mono.just(circulationPumpResponse));
//
//        sut.handlePumps(body);
//
//        verify(shellyClient, times(numberCirculationPump)).controlCirculationPump(anyBoolean());
//        verify(shellyClient, never()).getHotWaterPumpStatus();
//        verify(shellyClient, never()).getHeatingPumpStatus();
//        verify(shellyClient, never()).controlHotWaterPump(anyBoolean());
//        verify(shellyClient, never()).controlHeatingPump(anyBoolean());
//
//        assertThat(boilerConfig.getCirculation().isWorking()).isEqualTo(expectedCirculationPumpStatus);
//    }
//
//    private static Stream<Arguments> dataSourceForCirculationPumpOnly() {
//        return Stream.of(
//            Arguments.of(
//                "should start circulation pump",
//                Relay.builder().ison(true).build(),
//                DeviceRabbitMessage.builder().name("circulation").enabled(true).build(),
//                1, true
//            ),
//            Arguments.of(
//                "should stop circulation pump",
//                Relay.builder().ison(false).build(),
//                DeviceRabbitMessage.builder().name("circulation").enabled(false).build(),
//                1, false
//            )
//        );
//    }
//
//    @ParameterizedTest(name = "{0}")
//    @MethodSource("dataSourceForHotWaterPumpOnly")
//    void shouldHandleOnlyHotWaterAndHeatingPumps(
//        final String name,
//        final DeviceRabbitMessage body,
//        final ShellyProRelayResponse heatingPumpStatusResponse,
//        final Relay hotWaterRelayResponse,
//        final boolean expectedHotWaterPumpStatus,
//        final boolean expectedHeatingPumpStatus
//
//    ) {
//        when(shellyClient.getHeatingPumpStatus()).thenReturn(Mono.just(heatingPumpStatusResponse));
//        when(shellyClient.controlHeatingPump(true)).thenReturn(Mono.just(Relay.builder().ison(true).build()));
//        when(shellyClient.controlHeatingPump(false)).thenReturn(Mono.just(Relay.builder().ison(false).build()));
//        when(shellyClient.controlHotWaterPump(anyBoolean())).thenReturn(Mono.just(hotWaterRelayResponse));
//
//        sut.handlePumps(body);
//
//        verify(shellyClient, never()).controlCirculationPump(anyBoolean());
//        verify(shellyClient, never()).getHotWaterPumpStatus();
//        verify(shellyClient, times(1)).getHeatingPumpStatus();
//        verify(shellyClient, never()).controlHotWaterPump(anyBoolean());
//        verify(shellyClient, times()).controlHeatingPump(anyBoolean());
//
//        assertThat(boilerConfig.getHotWater().isWorking()).isEqualTo(expectedHotWaterPumpStatus);
//        assertThat(boilerConfig.getHeating().isWorking()).isEqualTo(expectedHeatingPumpStatus);
//    }
//
//    private static Stream<Arguments> dataSourceForHotWaterPumpOnly() {
//        return Stream.of(
//            Arguments.of(
//                "should start hot water pump when heating pump is off",
//                DeviceRabbitMessage.builder().name("hot_water").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                Relay.builder().ison(true).build(),
//                true, false
//            ),
//            Arguments.of(
//                "should start hot water pump when and stop heating pump if is on",
//                DeviceRabbitMessage.builder().name("hot_water").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(true).build(),
//                true, false
//            ),
//            Arguments.of(
//                "should stop hot water pump and not start heating pump when boiler status not enabled",
//                DeviceRabbitMessage.builder().name("hot_water").enabled(false).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                Relay.builder().ison(false).build(),
//                false, false
//            ),
//            Arguments.of(
//                "should stop hot water pump and start heating pump when boiler status is enabled",
//                DeviceRabbitMessage.builder().name("hot_water").enabled(false).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(false).build(),
//                false, true
//            )
//        );
//    }
}
