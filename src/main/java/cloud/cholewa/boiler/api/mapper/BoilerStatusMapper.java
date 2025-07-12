package cloud.cholewa.boiler.api.mapper;

import cloud.cholewa.boiler.api.model.BoilerStatusReply;
import cloud.cholewa.boiler.api.model.DeviceStatusReply;
import cloud.cholewa.boiler.api.model.LastMessageReply;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.DeviceType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BoilerStatusMapper {

    public static BoilerStatusReply toBoilerStatusReply(BoilerConfig boiler) {
        return BoilerStatusReply.builder()
            .furnace(getFurnaceReply(boiler))
            .pumps(getPumpsReply(boiler))
            .build();
    }

    private static DeviceStatusReply getFurnaceReply(final BoilerConfig boiler) {
        if (boiler.getFurnace().getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return DeviceStatusReply.builder()
            .isWorking(boiler.getFurnace().isWorking())
            .lastMessageReply(LastMessageReply.builder()
                .timestamp(boiler.getFurnace().getLastMessage().getTimestamp())
                .message(boiler.getFurnace().getLastMessage().getMessage())
                .build())
            .build();
    }

    private static Map<String, DeviceStatusReply> getPumpsReply(final BoilerConfig boiler) {
        return Map.ofEntries(
            Map.entry(DeviceType.CIRCULATION.name().toLowerCase(), getCirculationPumpReply(boiler)),
            Map.entry(DeviceType.HOT_WATER.name().toLowerCase(), getHotWaterPumpReply(boiler)),
            Map.entry(DeviceType.HEATING.name().toLowerCase(), getHeatingPumpReply(boiler)),
            Map.entry(DeviceType.FLOOR.name().toLowerCase(), getFloorPumpReply(boiler))
        );
    }

    private static DeviceStatusReply getCirculationPumpReply(final BoilerConfig boiler) {
        if (boiler.getCirculation().getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return DeviceStatusReply.builder()
            .isWorking(boiler.getCirculation().isWorking())
            .lastMessageReply(LastMessageReply.builder()
                .timestamp(boiler.getCirculation().getLastMessage().getTimestamp())
                .message(boiler.getCirculation().getLastMessage().getMessage())
                .build())
            .build();
    }

    private static DeviceStatusReply getHotWaterPumpReply(final BoilerConfig boiler) {
        if (boiler.getHotWater().getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return DeviceStatusReply.builder()
            .isWorking(boiler.getHotWater().isWorking())
            .lastMessageReply(LastMessageReply.builder()
                .timestamp(boiler.getHotWater().getLastMessage().getTimestamp())
                .message(boiler.getHotWater().getLastMessage().getMessage())
                .build())
            .build();
    }

    private static DeviceStatusReply getHeatingPumpReply(final BoilerConfig boiler) {
        if (boiler.getHeating().getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return DeviceStatusReply.builder()
            .isWorking(boiler.getHeating().isWorking())
            .lastMessageReply(LastMessageReply.builder()
                .timestamp(boiler.getHeating().getLastMessage().getTimestamp())
                .message(boiler.getHeating().getLastMessage().getMessage())
                .build())
            .build();
    }

    private static DeviceStatusReply getFloorPumpReply(final BoilerConfig boiler) {
        if (boiler.getFloor().getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return DeviceStatusReply.builder()
            .isWorking(boiler.getFloor().isWorking())
            .lastMessageReply(LastMessageReply.builder()
                .timestamp(boiler.getFloor().getLastMessage().getTimestamp())
                .message(boiler.getFloor().getLastMessage().getMessage())
                .build())
            .build();
    }
}
