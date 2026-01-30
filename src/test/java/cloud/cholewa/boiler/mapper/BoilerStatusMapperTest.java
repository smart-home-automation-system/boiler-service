package cloud.cholewa.boiler.mapper;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.BoilerDeviceType;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.boiler.model.LastMessage;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BoilerStatusMapperTest {

    private final BoilerStatusMapper sut = Mappers.getMapper(BoilerStatusMapper.class);

    @Test
    void should_map_boiler_config_to_reply() {
        // Given
        BoilerConfig boilerConfig = new BoilerConfig();

        LastMessage furnaceMessage = new LastMessage("Furnace message");
        furnaceMessage.setTimestamp(LocalDateTime.now());
        boilerConfig.getFurnace().setWorking(true);
        boilerConfig.getFurnace().setLastMessage(furnaceMessage);

        LastMessage waterMessage = new LastMessage("Water message");
        waterMessage.setTimestamp(LocalDateTime.now().minusMinutes(1));
        boilerConfig.getWater().setWorking(false);
        boilerConfig.getWater().setLastMessage(waterMessage);

        LastMessage heatingMessage = new LastMessage("Heating message");
        heatingMessage.setTimestamp(LocalDateTime.now().minusMinutes(2));
        boilerConfig.getHeating().setWorking(true);
        boilerConfig.getHeating().setLastMessage(heatingMessage);

        // When
        BoilerStatusReply result = sut.toBoilerStatusReply(boilerConfig);

        // Then
        assertThat(result).isNotNull();

        assertThat(result.getFurnace()).isNotNull();
        assertThat(result.getFurnace().isWorking()).isTrue();
        assertThat(result.getFurnace().getLastMessageReply().getMessage()).isEqualTo("Furnace message");

        assertThat(result.getPumps()).hasSize(2);

        assertThat(result.getPumps().get(BoilerDeviceType.HOT_WATER.name().toLowerCase())).isNotNull();
        assertThat(result.getPumps().get(BoilerDeviceType.HOT_WATER.name().toLowerCase()).isWorking()).isFalse();
        assertThat(result.getPumps().get(BoilerDeviceType.HOT_WATER.name().toLowerCase()).getLastMessageReply().getMessage()).isEqualTo(
            "Water message");

        assertThat(result.getPumps().get(BoilerDeviceType.HEATING.name().toLowerCase())).isNotNull();
        assertThat(result.getPumps().get(BoilerDeviceType.HEATING.name().toLowerCase()).isWorking()).isTrue();
        assertThat(result.getPumps().get(BoilerDeviceType.HEATING.name().toLowerCase()).getLastMessageReply().getMessage()).isEqualTo(
            "Heating message");
    }

    @Test
    void should_map_empty_boiler_config() {
        BoilerConfig boilerConfig = new BoilerConfig();

        BoilerStatusReply result = sut.toBoilerStatusReply(boilerConfig);

        assertThat(result).isNotNull();
        assertThat(result.getFurnace().getLastMessageReply()).isNull();
        assertThat(result.getPumps().get(BoilerDeviceType.HOT_WATER.name().toLowerCase()).getLastMessageReply()).isNull();
        assertThat(result.getPumps().get(BoilerDeviceType.HEATING.name().toLowerCase()).getLastMessageReply()).isNull();
    }
}
