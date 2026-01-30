package cloud.cholewa.boiler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceStatusReply {
    private boolean isWorking;
    private LastMessageReply lastMessageReply;
}
