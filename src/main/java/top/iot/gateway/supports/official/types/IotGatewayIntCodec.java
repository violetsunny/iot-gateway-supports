package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.IntType;

@Getter
@Setter
public class IotGatewayIntCodec extends IotGatewayNumberCodec<IntType> {

    @Override
    public String getTypeId() {
        return IntType.ID;
    }


}
