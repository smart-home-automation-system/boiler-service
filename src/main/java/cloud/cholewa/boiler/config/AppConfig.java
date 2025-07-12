package cloud.cholewa.boiler.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final Logbook logbook;

    @Bean
    ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("shellyConnectionProvider")
            .maxConnections(50)
            .build();
    }

    @Bean
    HttpClient httpClient(final ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .doOnConnected(conn -> conn
                .addHandlerLast(new LogbookClientHandler(logbook))
            );
    }

    @Bean
    WebClient shellyWebClient(final WebClient.Builder webClientBuilder, final HttpClient httpClient) {
        return webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
