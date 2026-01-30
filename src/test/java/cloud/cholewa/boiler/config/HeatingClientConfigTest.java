package cloud.cholewa.boiler.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class HeatingClientConfigTest {

    private final HeatingClientConfig heatingClientConfig = new HeatingClientConfig();

    @Test
    void should_return_correct_url() {
        ReflectionTestUtils.setField(heatingClientConfig, "schema", "http");
        ReflectionTestUtils.setField(heatingClientConfig, "host", "localhost");
        ReflectionTestUtils.setField(heatingClientConfig, "port", 8080);
        ReflectionTestUtils.setField(heatingClientConfig, "path", "/");

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();

        heatingClientConfig.getUriBuilder(uriBuilder);
        String result = uriBuilder.build().toUriString();

        String expected = "http://localhost:8080/";

        assertThat(result).isEqualTo(expected);
    }
}
