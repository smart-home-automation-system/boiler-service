package cloud.cholewa.boiler.service;

import cloud.cholewa.boiler.mapper.BoilerStatusMapper;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoilerServiceTest {

    @Mock
    private BoilerStatusMapper boilerStatusMapper;

    @InjectMocks
    private BoilerService sut;

    @Test
    void should_return_boiler_status() {
        when(boilerStatusMapper.toBoilerStatusReply(any())).thenReturn(BoilerStatusReply.builder().build());

        sut.getBoilerStatus()
            .as(StepVerifier::create)
            .assertNext(reply ->
                assertThat(reply).isInstanceOf(BoilerStatusReply.class))
            .verifyComplete();
    }
}
