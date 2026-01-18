package cloud.cholewa.boiler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BoilerStatusReply {
    private DeviceStatusReply furnace;
    private Map<String, DeviceStatusReply> pumps;
}
