package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoilerService {

    private final BoilerConfig boilerConfig;

    public Mono<BoilerStatusReply> getBoilerStatus() {
        return Mono.fromCallable(() -> BoilerStatusMapper.toBoilerStatusReply(boilerConfig))
            .doOnSubscribe(subscription -> log.info("Received request for boiler status"));
    }
}
