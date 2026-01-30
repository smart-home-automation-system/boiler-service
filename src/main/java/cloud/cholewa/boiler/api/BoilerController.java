package cloud.cholewa.boiler.api;

import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.boiler.service.BoilerService;
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

    private final BoilerService boilerService;

    @GetMapping("status")
    Mono<ResponseEntity<BoilerStatusReply>> getBoilerStatus() {
        return boilerService.getBoilerStatus()
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
