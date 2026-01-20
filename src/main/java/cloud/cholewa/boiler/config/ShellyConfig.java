package cloud.cholewa.boiler.config;

import cloud.cholewa.boiler.model.BoilerDeviceType;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;

@Getter
@Configuration
public class ShellyConfig {

    private static final String RELAY_PATH = "relay/";
    private static final String PRO4_STATUS_PATH = "rpc/Switch.GetStatus";

    @Value("${shelly.actor.pro.boiler.host}")
    private String boilerHost;
    @Value("${shelly.actor.pro.boiler.port}")
    private int boilerPort;
    @Value("${shelly.actor.pro.boiler.relay.furnace}")
    private String relayFurnace;
    @Value("${shelly.actor.pro.boiler.relay.hot-water}")
    private String relayWaterPump;
    @Value("${shelly.actor.pro.boiler.relay.heating}")
    private String relayHeating;

    public UriBuilder getControlUriBuilder(final UriBuilder uriBuilder, BoilerDeviceType boilerDeviceType) {
        return switch (boilerDeviceType) {
            case HOT_WATER -> uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(RELAY_PATH + relayWaterPump);
            case HEATING -> uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(RELAY_PATH + relayHeating);
            case FURNACE -> uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(RELAY_PATH + relayFurnace);
        };
    }

    public UriBuilder getStatusUriBuilder(final UriBuilder uriBuilder, final BoilerDeviceType boilerDeviceType) {
        return switch (boilerDeviceType) {
            case HOT_WATER ->
                uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(PRO4_STATUS_PATH).queryParam("id", relayWaterPump);
            case HEATING ->
                uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(PRO4_STATUS_PATH).queryParam("id", relayHeating);
            case FURNACE ->
                uriBuilder.scheme("http").host(boilerHost).port(boilerPort).path(PRO4_STATUS_PATH).queryParam("id", relayFurnace);
        };
    }
}
