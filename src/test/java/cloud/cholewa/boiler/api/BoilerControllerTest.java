package cloud.cholewa.boiler.api;

import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.boiler.service.BoilerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(BoilerController.class)
class BoilerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BoilerService boilerService;

    @Test
    void should_return_404_when_status_not_found() {
        when(boilerService.getBoilerStatus()).thenReturn(Mono.empty());

        webTestClient.get()
            .uri("/status")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void should_return_200_when_status_found() {
        when(boilerService.getBoilerStatus()).thenReturn(Mono.just(BoilerStatusReply.builder().build()));

        webTestClient.get()
            .uri("/status")
            .exchange()
            .expectStatus().isOk();
    }
}
