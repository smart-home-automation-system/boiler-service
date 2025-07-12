package cloud.cholewa.boiler.api;

import cloud.cholewa.boiler.api.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.api.model.BoilerStatusReply;
import cloud.cholewa.boiler.config.BoilerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BoilerController {

    private final BoilerConfig boiler;

    @GetMapping("status")
    Mono<ResponseEntity<BoilerStatusReply>> getStatus() {
        return Mono.just(BoilerStatusMapper.toBoilerStatusReply(boiler))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
