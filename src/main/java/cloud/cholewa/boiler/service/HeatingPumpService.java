package cloud.cholewa.boiler.service;

import cloud.cholewa.home.model.SystemActiveReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatingPumpService {

    public Mono<Void> controlPump(final SystemActiveReply heatingSystemActiveReply) {
        return Mono.empty();
    }
}
