package cloud.cholewa.boiler.client;

import cloud.cholewa.boiler.config.WaterClientConfig;
import cloud.cholewa.home.model.SystemActiveReply;
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

import static org.assertj.core.api.Assertions.assertThat;

class WaterClientTest {

    private MockWebServer mockWebServer;
    private WaterClient sut;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WaterClientConfig config = getWaterClientConfig();

        WebClient webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        sut = new WaterClient(config, webClient);
    }

    @AfterEach
    void tearDown() {
        mockWebServer.close();
    }

    @Test
    void should_receive_water_status_when_server_returns_ok() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(HttpStatus.OK.value())
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""
                {
                "active": true
                }
                """)
            .build()
        );

        sut.querySystemActive()
            .as(StepVerifier::create)
            .assertNext(systemActiveReply ->
                assertThat(systemActiveReply)
                    .isNotNull()
                    .isInstanceOf(SystemActiveReply.class)
                    .satisfies(reply -> assertThat(reply.getActive()).isTrue())
            )
            .verifyComplete();
    }

    @Test
    void should_return_false_water_status_when_server_returns_error() {
        mockWebServer.enqueue(new MockResponse.Builder().code(HttpStatus.INTERNAL_SERVER_ERROR.value()).build());

        sut.querySystemActive()
            .as(StepVerifier::create)
            .assertNext(
                systemActiveReply ->
                    assertThat(systemActiveReply)
                        .isInstanceOf(SystemActiveReply.class)
                        .satisfies(reply -> assertThat(reply.getActive()).isFalse())
            )
            .verifyComplete();
    }

    private WaterClientConfig getWaterClientConfig() {
        WaterClientConfig config = new WaterClientConfig();
        ReflectionTestUtils.setField(config, "schema", "http");
        ReflectionTestUtils.setField(config, "host", mockWebServer.getHostName());
        ReflectionTestUtils.setField(config, "port", mockWebServer.getPort());
        ReflectionTestUtils.setField(config, "path", "/");
        return config;
    }
}
