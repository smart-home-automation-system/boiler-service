package cloud.cholewa.boiler.furnace;

import cloud.cholewa.boiler.config.BoilerConfig;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FurnaceServiceTest {

//    private FurnaceService sut;
//    private BoilerConfig boilerConfig;
//    @Mock
//    private ShellyClient shellyClient;
//
//    @BeforeEach
//    void setUp() {
//        boilerConfig = new BoilerConfig();
//
//        sut = new FurnaceService(boilerConfig, shellyClient);
//    }
//
//    @Test
//    void shouldThrowIllegalArgumentExceptionWhenInvalidDeviceType() {
//        DeviceRabbitMessage body = DeviceRabbitMessage.builder().name(("InvalidDeviceType")).build();
//
//        assertThatExceptionOfType(IllegalArgumentException.class)
//            .isThrownBy(() -> sut.handleFurnace(body));
//    }
//
//    @ParameterizedTest(name = "{0}")
//    @MethodSource("furnaceConfiguration")
//    void shouldEnableFurnaceWhenBothPumpsAreWorking(
//        final String name,
//        final DeviceRabbitMessage body,
//        final ShellyProRelayResponse hotWaterPumpResponse,
//        final ShellyProRelayResponse heatingPumpResponse,
//        final Relay furnaceRelayResponse,
//        final int numberHotWaterPump,
//        final int numberHeatingPump,
//        final int numberFurnace,
//        final boolean statusFurnace
//    ) {
//        lenient().when(shellyClient.getHeatingPumpStatus()).thenReturn(Mono.just(hotWaterPumpResponse));
//        lenient().when(shellyClient.getHotWaterPumpStatus()).thenReturn(Mono.just(heatingPumpResponse));
//        when(shellyClient.controlFurnace(anyBoolean())).thenReturn(Mono.just(furnaceRelayResponse));
//
//        sut.handleFurnace(body);
//
//        verify(shellyClient, Mockito.times(numberHotWaterPump)).getHotWaterPumpStatus();
//        verify(shellyClient, Mockito.times(numberHeatingPump)).getHeatingPumpStatus();
//        verify(shellyClient, Mockito.times(numberFurnace)).controlFurnace(anyBoolean());
//        assertThat(boilerConfig.getFurnace().isWorking()).isEqualTo(statusFurnace);
//    }
//
//    private static Stream<Arguments> furnaceConfiguration() {
//        return Stream.of(
//            Arguments.of(
//                "set furnace on, both pumps are working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(true).build(),
//                1, 1, 1, true
//            ),
//            Arguments.of(
//                "set furnace on, when heating pump is working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(true).build(),
//                1, 1, 1, true
//            ),
//            Arguments.of(
//                "set furnace on, when hot water pump is working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                Relay.builder().ison(true).build(),
//                1, 1, 1, true
//            ),
//            Arguments.of(
//                "dont set furnace on, when no pumps are working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(true).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                Relay.builder().ison(false).build(),
//                1, 1, 1, false
//            ),
//            Arguments.of(
//                "set furnace off, even if heating pump is working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(false).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(false).build(),
//                0, 0, 1, false
//            ),
//            Arguments.of(
//                "set furnace off, even if hot water pump is working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(false).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                Relay.builder().ison(false).build(),
//                0, 0, 1, false
//            ),
//            Arguments.of(
//                "set furnace off, even if both pumps are working",
//                DeviceRabbitMessage.builder().name("furnace").enabled(false).build(),
//                ShellyProRelayResponse.builder().ison(false).build(),
//                ShellyProRelayResponse.builder().ison(true).build(),
//                Relay.builder().ison(false).build(),
//                0, 0, 1, false
//            )
//        );
//    }
}
