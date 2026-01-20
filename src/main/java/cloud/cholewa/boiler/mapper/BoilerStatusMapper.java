package cloud.cholewa.boiler.mapper;

import cloud.cholewa.boiler.config.BoilerConfig;
import cloud.cholewa.boiler.model.BoilerDeviceType;
import cloud.cholewa.boiler.model.BoilerStatusReply;
import cloud.cholewa.boiler.model.DeviceStatusReply;
import cloud.cholewa.boiler.model.DeviceStatus;
import cloud.cholewa.boiler.model.LastMessage;
import cloud.cholewa.boiler.model.LastMessageReply;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface BoilerStatusMapper {

    @Mapping(target = "pumps", expression = "java(getPumpsReply(boiler))")
    @Mapping(target = "furnace", expression = "java(mapDeviceStatus(boiler.getFurnace()))")
    BoilerStatusReply toBoilerStatusReply(BoilerConfig boiler);

    @Mapping(target = "isWorking", expression = "java(deviceStatus.isWorking())")
    @Mapping(target = "lastMessageReply", source = "lastMessage")
    DeviceStatusReply toDeviceStatusReply(DeviceStatus deviceStatus);

    LastMessageReply toLastMessageReply(LastMessage lastMessage);

    default DeviceStatusReply mapDeviceStatus(DeviceStatus deviceStatus) {
        if (deviceStatus == null || deviceStatus.getLastMessage() == null) {
            return DeviceStatusReply.builder().build();
        }
        return toDeviceStatusReply(deviceStatus);
    }

    default Map<String, DeviceStatusReply> getPumpsReply(final BoilerConfig boiler) {
        return Map.ofEntries(
            Map.entry(BoilerDeviceType.HOT_WATER.name().toLowerCase(), mapDeviceStatus(boiler.getWater())),
            Map.entry(BoilerDeviceType.HEATING.name().toLowerCase(), mapDeviceStatus(boiler.getHeating()))
        );
    }
}
