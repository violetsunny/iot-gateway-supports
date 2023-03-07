package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.FloatType;

@Getter
@Setter
public class IotGatewayFloatCodec extends IotGatewayNumberCodec<FloatType> {

    @Override
    public String getTypeId() {
        return FloatType.ID;
    }

}
