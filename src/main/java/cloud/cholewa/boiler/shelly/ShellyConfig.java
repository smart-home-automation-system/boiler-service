package cloud.cholewa.boiler.shelly;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;

@Configuration
class ShellyConfig {

    @Value("${shelly.actor.uni.circulation.host}")
    private String circulationPumpHost;

    public UriBuilder getUriBuilder(final UriBuilder uriBuilder) {
        return uriBuilder.scheme("http").host(circulationPumpHost);
    }
}
