package cloud.cholewa.boiler.model;

import lombok.Data;

@Data
public class DeviceRabbitMessage {
    private String name;
    private boolean enabled;
}
