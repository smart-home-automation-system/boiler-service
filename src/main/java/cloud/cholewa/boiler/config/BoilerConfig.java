package cloud.cholewa.boiler.config;

import cloud.cholewa.boiler.model.DeviceStatus;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class BoilerConfig {

    private final DeviceStatus furnace = DeviceStatus.builder().build();
    private final DeviceStatus circulation = DeviceStatus.builder().build();
    private final DeviceStatus hotWater = DeviceStatus.builder().build();
    private final DeviceStatus heating = DeviceStatus.builder().build();
    private final DeviceStatus floor = DeviceStatus.builder().build();
}
