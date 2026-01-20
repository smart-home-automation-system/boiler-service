package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.ShellyConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyProRelayResponse;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ShellyClientTest {

    private MockWebServer mockWebServer;
    private ShellyClient sut;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        ShellyConfig config = new ShellyConfig();

        ReflectionTestUtils.setField(config, "boilerHost", mockWebServer.getHostName());
        ReflectionTestUtils.setField(config, "boilerPort", mockWebServer.getPort());
        ReflectionTestUtils.setField(config, "relayFurnace", "0");
        ReflectionTestUtils.setField(config, "relayWaterPump", "1");
        ReflectionTestUtils.setField(config, "relayHeating", "2");

        sut = new ShellyClient(config, WebClient.create());
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        mockWebServer.shutdown();
    }

    @Test
    void should_get_water_pump_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": true
                }
                """)
        );

        sut.getWaterPumpStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyProRelayResponse.class);
                    assertThat(response.getIson()).isTrue();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_water_pump_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
        );

        sut.getWaterPumpStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_water_pump() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": false
                }
                """)
        );

        sut.controlWaterPump(false)
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(Relay.class);
                    assertThat(response.getIson()).isFalse();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_controlling_water_pump() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.BAD_GATEWAY.value())
        );

        sut.controlWaterPump(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_get_heating_pump_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": false
                }
                """)
        );

        sut.getHeatingPumpStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyProRelayResponse.class);
                    assertThat(response.getIson()).isFalse();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_heating_pump_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.REQUEST_TIMEOUT.value())
        );

        sut.getHeatingPumpStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_heating_pump() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": false
                }
                """)
        );

        sut.controlHeatingPump(false)
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(Relay.class);
                    assertThat(response.getIson()).isFalse();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_controlling_heating_pump() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value())
        );

        sut.controlHeatingPump(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_get_furnace_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": true
                }
                """)
        );

        sut.getFurnaceStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyProRelayResponse.class);
                    assertThat(response.getIson()).isTrue();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_furnace_status() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.BAD_GATEWAY.value())
        );

        sut.getFurnaceStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_furnace() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "ison": false
                }
                """)
        );

        sut.controlFurnace(false)
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(Relay.class);
                    assertThat(response.getIson()).isFalse();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_controlling_furnace() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.GATEWAY_TIMEOUT.value())
        );

        sut.controlFurnace(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }
}
