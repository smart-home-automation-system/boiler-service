package cloud.cholewa.boiler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;

import java.net.URI;

@Configuration
public class HeatingClientConfig {

    @Value("${internal.service.heating-service.schema}")
    private String schema;

    @Value("${internal.service.heating-service.host}")
    private String host;

    @Value("${internal.service.heating-service.port}")
    private int port;

    @Value("${internal.service.heating-service.path}")
    private String path;

    public URI getUriBuilder(final UriBuilder uriBuilder) {
        return uriBuilder.scheme(schema).host(host).port(port).path(path).build();
    }
}
