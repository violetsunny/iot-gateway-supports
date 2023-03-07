package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.DoubleType;

@Getter
@Setter
public class IotGatewayDoubleCodec extends IotGatewayNumberCodec<DoubleType> {

    @Override
    public String getTypeId() {
        return DoubleType.ID;
    }

}
