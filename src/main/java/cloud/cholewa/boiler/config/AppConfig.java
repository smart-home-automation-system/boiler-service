package cloud.cholewa.boiler.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

//The calls of every WebClient here are logged by Logbook from inside the reactive chain, with their
//traceId: cholewa-commons >= 1.10.0 puts its exchange filter on the injected WebClient.Builder (HAS-306).
//A LogbookClientHandler on these HttpClients would log every call a second time, without the trace
@Configuration
@RequiredArgsConstructor
public class AppConfig {

    @Bean
    ConnectionProvider shellyConnectionProvider() {
        return ConnectionProvider.builder("shellyConnectionProvider")
            .maxConnections(50)
            .build();
    }

    @Bean
    ConnectionProvider heatingConnectionProvider() {
        return ConnectionProvider.builder("heatingConnectionProvider")
            .maxConnections(50)
            .build();
    }

    @Bean
    ConnectionProvider waterConnectionProvider() {
        return ConnectionProvider.builder("waterConnectionProvider")
            .maxConnections(50)
            .build();
    }

    @Bean
    HttpClient shellyHttpClient(final ConnectionProvider shellyConnectionProvider) {
        return HttpClient.create(shellyConnectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(5));
    }

    @Bean
    HttpClient heatingHttpClient(final ConnectionProvider heatingConnectionProvider) {
        return HttpClient.create(heatingConnectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(5));
    }

    @Bean
    HttpClient waterHttpClient(final ConnectionProvider waterConnectionProvider) {
        return HttpClient.create(waterConnectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(5));
    }

    @Bean
    WebClient shellyWebClient(final WebClient.Builder webClientBuilder, final HttpClient shellyHttpClient) {
        return webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(shellyHttpClient))
            .build();
    }

    @Bean
    WebClient heatingWebClient(final WebClient.Builder webClientBuilder, final HttpClient heatingHttpClient) {
        return webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(heatingHttpClient))
            .build();
    }

    @Bean
    WebClient waterWebClient(final WebClient.Builder webClientBuilder, final HttpClient waterHttpClient) {
        return webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(waterHttpClient))
            .build();
    }
}
