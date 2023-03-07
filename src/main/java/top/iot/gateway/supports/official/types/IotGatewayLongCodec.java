package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.LongType;

@Getter
@Setter
public class IotGatewayLongCodec extends IotGatewayNumberCodec<LongType> {

    @Override
    public String getTypeId() {
        return LongType.ID;
    }

}
