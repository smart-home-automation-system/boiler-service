package cloud.cholewa.boiler.mapper;

import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.boiler.model.DeviceStatusReply;
import cloud.cholewa.boiler.model.LastMessageReply;
import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.BoilerDeviceType;
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
            Map.entry(BoilerDeviceType.CIRCULATION.name().toLowerCase(), getCirculationPumpReply(boiler)),
            Map.entry(BoilerDeviceType.HOT_WATER.name().toLowerCase(), getHotWaterPumpReply(boiler)),
            Map.entry(BoilerDeviceType.HEATING.name().toLowerCase(), getHeatingPumpReply(boiler))
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
}
