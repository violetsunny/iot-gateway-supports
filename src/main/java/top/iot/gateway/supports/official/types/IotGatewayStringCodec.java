package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.StringType;

@Getter
@Setter
public class IotGatewayStringCodec extends AbstractDataTypeCodec<StringType> {

    @Override
    public String getTypeId() {
        return StringType.ID;
    }

}
