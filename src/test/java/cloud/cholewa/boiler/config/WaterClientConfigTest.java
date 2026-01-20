package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class WaterClientConfigTest {

    private final WaterClientConfig waterClientConfig = new WaterClientConfig();

    @Test
    void should_return_correct_url() {
        ReflectionTestUtils.setField(waterClientConfig, "schema", "http");
        ReflectionTestUtils.setField(waterClientConfig, "host", "10.20.30.40");
        ReflectionTestUtils.setField(waterClientConfig, "port", 1234);
        ReflectionTestUtils.setField(waterClientConfig, "path", "/test");

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        waterClientConfig.getUriBuilder(uriBuilder);
        URI result = uriBuilder.build().toUri();

        assertThat(result.getScheme()).isEqualTo("http");
        assertThat(result.getPort()).isEqualTo(1234);
        assertThat(result.getHost()).isEqualTo("10.20.30.40");
        assertThat(result.getPath()).isEqualTo("/test");
    }
}
