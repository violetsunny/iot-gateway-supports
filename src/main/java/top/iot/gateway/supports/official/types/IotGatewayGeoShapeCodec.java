package top.iot.gateway.supports.official.types;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.types.GeoShapeType;

@Getter
@Setter
public class IotGatewayGeoShapeCodec extends AbstractDataTypeCodec<GeoShapeType> {

    @Override
    public String getTypeId() {
        return GeoShapeType.ID;
    }

}
