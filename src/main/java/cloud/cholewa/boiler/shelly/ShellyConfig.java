package cloud.cholewa.boiler.shelly;

import cloud.cholewa.boiler.infrastructure.error.BoilerException;
import cloud.cholewa.boiler.model.DeviceType;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;

@Getter
@Configuration
class ShellyConfig {

    private static final String RELAY_PATH = "relay/";
    private static final String PRO4_STATUS_PATH = "rpc/Switch.GetStatus";

    @Value("${shelly.actor.uni.circulation.host}")
    private String circulationPumpHost;

    @Value("${shelly.actor.pro.boiler.host}")
    private String boilerHost;

    @Value("${shelly.actor.pro.floor.host}")
    private String floorHost;

    @Value("${shelly.actor.pro.boiler.relay.furnace}")
    private String relayFurnace;

    @Value("${shelly.actor.uni.circulation.relay}")
    private String relayCirculationPump;

    @Value("${shelly.actor.pro.boiler.relay.hot-water}")
    private String relayHotWaterPump;

    @Value("${shelly.actor.pro.boiler.relay.heating}")
    private String relayHeating;

    @Value("${shelly.actor.pro.floor.relay}")
    private String relayFloorPump;

    public UriBuilder getControlUriBuilder(final UriBuilder uriBuilder, DeviceType deviceType) {

        return switch (deviceType) {
            case CIRCULATION -> uriBuilder.scheme("http").host(circulationPumpHost).path(RELAY_PATH + relayCirculationPump);
            case HOT_WATER -> uriBuilder.scheme("http").host(boilerHost).path(RELAY_PATH + relayHotWaterPump);
            case HEATING -> uriBuilder.scheme("http").host(boilerHost).path(RELAY_PATH + relayHeating);
            case FURNACE -> uriBuilder.scheme("http").host(boilerHost).path(RELAY_PATH + relayFurnace);
            case FLOOR -> uriBuilder.scheme("http").host(floorHost).path(RELAY_PATH + relayFloorPump);
        };
    }

    public UriBuilder getStatusUriBuilder(final UriBuilder uriBuilder, DeviceType deviceType) {
        return switch (deviceType) {
            case HOT_WATER ->
                uriBuilder.scheme("http").host(boilerHost).path(PRO4_STATUS_PATH).queryParam("id", relayHotWaterPump);
            case HEATING ->
                uriBuilder.scheme("http").host(boilerHost).path(PRO4_STATUS_PATH).queryParam("id", relayHeating);
            case FLOOR ->
                uriBuilder.scheme("http").host(floorHost).path(PRO4_STATUS_PATH).queryParam("id", relayFloorPump);
            default -> throw new BoilerException("Unexpected value for getting status of device: " + deviceType);
        };
    }
}
