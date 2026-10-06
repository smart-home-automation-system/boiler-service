package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.ShellyConfig;
import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.shelly.model.Relay;
import cloud.cholewa.shelly.model.ShellyPro4StatusResponse;
import lombok.SneakyThrows;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ShellyClientTest {

    private final ShellyCallListener callListener = mock(ShellyCallListener.class);

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

        sut = new ShellyClient(config, WebClient.create(), callListener);
    }

    @AfterEach
    void tearDown() {
        mockWebServer.close();
    }

    //the listener decides when the household is told the device is gone, from these two signals
    @Test
    void should_tell_the_listener_that_the_device_answered() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("{\"ison\": true}")
            .build()
        );

        sut.controlFurnace(true).as(StepVerifier::create).expectNextCount(1).verifyComplete();

        verify(callListener).recordAnswer();
        verifyNoMoreInteractions(callListener);
    }

    @Test
    void should_tell_the_listener_that_the_device_answered_with_an_error() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .build()
        );

        sut.getFurnaceStatus().as(StepVerifier::create).verifyError(BoilerException.class);

        verify(callListener).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    //a 200 that is not the answer of a Shelly - a captive portal, another device on the address
    @Test
    void should_tell_the_listener_that_the_answer_was_not_one() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("{ not json")
            .build()
        );

        sut.getHeatingPumpStatus().as(StepVerifier::create).verifyError(BoilerException.class);

        verify(callListener).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    //valid JSON of another shape decodes into an object of nulls, which read as "the relay is off"
    @Test
    void should_tell_the_listener_that_the_answer_was_not_the_one_of_a_shelly() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("{\"code\": -103, \"message\": \"No handler\"}")
            .build()
        );
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("{}")
            .build()
        );

        sut.getFurnaceStatus().as(StepVerifier::create).verifyError(BoilerException.class);
        sut.controlFurnace(false).as(StepVerifier::create).verifyError(BoilerException.class);

        verify(callListener, times(2)).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    //completes without a value, so without this it would count as neither
    @Test
    void should_tell_the_listener_that_the_answer_was_empty() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .build()
        );

        sut.getWaterPumpStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable)
                .isInstanceOf(BoilerException.class)
                .hasMessage("Error fetching water pump status"));

        verify(callListener).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    //a live device refusing the call - authentication switched on, a path changed by a firmware
    //update - is not driven either
    @Test
    void should_tell_the_listener_that_the_device_refused_the_call() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.UNAUTHORIZED.value())
            .build()
        );

        sut.controlHeatingPump(true).as(StepVerifier::create).verifyError(BoilerException.class);

        verify(callListener).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    @Test
    void should_tell_the_listener_that_the_device_is_unreachable() {
        mockWebServer.close();

        sut.controlWaterPump(true).as(StepVerifier::create).verifyError(BoilerException.class);

        verify(callListener).recordFailure();
        verifyNoMoreInteractions(callListener);
    }

    @Test
    void should_get_water_pump_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "output": true
                }
                """)
            .build()
        );

        sut.getWaterPumpStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyPro4StatusResponse.class);
                    assertThat(response.getOutput()).isTrue();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_water_pump_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .build()
        );

        sut.getWaterPumpStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_water_pump() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "ison": false
                }
                """)
            .build()
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
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.BAD_GATEWAY.value())
            .build()
        );

        sut.controlWaterPump(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_get_heating_pump_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "output": false
                }
                """)
            .build()
        );

        sut.getHeatingPumpStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyPro4StatusResponse.class);
                    assertThat(response.getOutput()).isFalse();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_heating_pump_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.REQUEST_TIMEOUT.value())
            .build()
        );

        sut.getHeatingPumpStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_heating_pump() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "ison": false
                }
                """)
            .build()
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
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.TOO_MANY_REQUESTS.value())
            .build()
        );

        sut.controlHeatingPump(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_get_furnace_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "output": true
                }
                """)
            .build()
        );

        sut.getFurnaceStatus()
            .as(StepVerifier::create)
            .assertNext(
                response -> {
                    assertThat(response).isInstanceOf(ShellyPro4StatusResponse.class);
                    assertThat(response.getOutput()).isTrue();
                }
            )
            .verifyComplete();
    }

    @Test
    void should_throw_exception_when_error_during_getting_furnace_status() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.BAD_GATEWAY.value())
            .build()
        );

        sut.getFurnaceStatus()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }

    @Test
    void should_control_furnace() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                    "ison": false
                }
                """)
            .build()
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
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.GATEWAY_TIMEOUT.value())
            .build()
        );

        sut.controlFurnace(false)
            .as(StepVerifier::create)
            .verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(BoilerException.class));
    }
}
