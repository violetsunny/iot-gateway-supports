package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.PasswordType;

@Getter
@Setter
public class IotGatewayPasswordCodec extends AbstractDataTypeCodec<PasswordType> {

    @Override
    public String getTypeId() {
        return PasswordType.ID;
    }

}
