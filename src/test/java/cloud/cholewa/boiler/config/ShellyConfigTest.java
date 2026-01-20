package cloud.cholewa.boiler.config;

import cloud.cholewa.boiler.model.BoilerDeviceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ShellyConfigTest {

    private final ShellyConfig sut = new ShellyConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "boilerHost", "192.168.1.10");
        ReflectionTestUtils.setField(sut, "relayFurnace", "0");
        ReflectionTestUtils.setField(sut, "relayHotWaterPump", "1");
        ReflectionTestUtils.setField(sut, "relayHeating", "2");
    }

    @ParameterizedTest
    @EnumSource(BoilerDeviceType.class)
    void should_return_correct_control_uri(final BoilerDeviceType type) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();

        sut.getControlUriBuilder(uriBuilder, type);
        URI result = uriBuilder.build().toUri();

        String expectedRelay = switch (type) {
            case FURNACE -> "0";
            case HOT_WATER -> "1";
            case HEATING -> "2";
        };

        assertThat(result.getScheme()).isEqualTo("http");
        assertThat(result.getHost()).isEqualTo("192.168.1.10");
        assertThat(result.getPath()).isEqualTo("/relay/" + expectedRelay);
    }

    @ParameterizedTest
    @EnumSource(BoilerDeviceType.class)
    void should_return_correct_status_uri(final BoilerDeviceType type) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();

        sut.getStatusUriBuilder(uriBuilder, type);
        URI result = uriBuilder.build().toUri();

        String expectedRelay = switch (type) {
            case FURNACE -> "0";
            case HOT_WATER -> "1";
            case HEATING -> "2";
        };

        assertThat(result.getScheme()).isEqualTo("http");
        assertThat(result.getHost()).isEqualTo("192.168.1.10");
        assertThat(result.getPath()).isEqualTo("/rpc/Switch.GetStatus");
        assertThat(result.getQuery()).isEqualTo("id=" + expectedRelay);
    }
}
