package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.HeatingClientConfig;
import cloud.cholewa.home.model.SystemActiveReply;
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

import static org.assertj.core.api.Assertions.assertThat;

class HeatingClientTest {

    private MockWebServer mockWebServer;
    private HeatingClient sut;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        HeatingClientConfig config = getHeatingClientConfig();
        WebClient webClient = WebClient.create();

        sut = new HeatingClient(config, webClient);
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        mockWebServer.shutdown();
    }

    @Test
    void should_receive_heating_status_when_system_is_inactive() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "active": false
                }
                """)
        );

        sut.querySystemActive()
            .as(StepVerifier::create)
            .assertNext(
                systemActiveReply ->
                    assertThat(systemActiveReply)
                        .isNotNull()
                        .isInstanceOf(SystemActiveReply.class)
                        .satisfies(reply -> assertThat(reply.getActive()).isFalse())
            )
            .verifyComplete();
    }

    @Test
    void should_return_false_heating_status_when_server_returns_error() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        sut.querySystemActive()
            .as(StepVerifier::create)
            .assertNext(systemActiveReply ->
                assertThat(systemActiveReply)
                    .isInstanceOf(SystemActiveReply.class)
                    .satisfies(reply -> assertThat(reply.getActive()).isFalse()))
            .verifyComplete();
    }

    private HeatingClientConfig getHeatingClientConfig() {
        HeatingClientConfig config = new HeatingClientConfig();
        ReflectionTestUtils.setField(config, "schema", "http");
        ReflectionTestUtils.setField(config, "host", mockWebServer.getHostName());
        ReflectionTestUtils.setField(config, "port", mockWebServer.getPort());
        ReflectionTestUtils.setField(config, "path", "/");
        return config;
    }
}
