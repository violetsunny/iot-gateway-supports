package top.iot.gateway.supports.official.types;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.DataType;
import top.iot.gateway.core.metadata.types.ArrayType;
import top.iot.gateway.core.metadata.types.DataTypes;
import top.iot.gateway.supports.official.IotGatewayDataTypeCodecs;

import java.util.Map;

import static java.util.Optional.ofNullable;

@Getter
@Setter
public class IotGatewayArrayCodec extends AbstractDataTypeCodec<ArrayType> {

    @Override
    public String getTypeId() {
        return ArrayType.ID;
    }

    @Override
    public ArrayType decode(ArrayType type, Map<String, Object> config) {
        super.decode(type, config);
        JSONObject jsonObject = new JSONObject(config);
        ofNullable(jsonObject.get("elementType"))
                .map(v -> {
                    if (v instanceof Map) {
                        return new JSONObject(((Map) v));
                    }
                    JSONObject eleType = new JSONObject();
                    eleType.put("type", v);
                    return eleType;
                })
                .map(eleType -> {
                    DataType dataType = DataTypes.lookup(eleType.getString("type")).get();

                    IotGatewayDataTypeCodecs.getCodec(dataType.getId())
                            .ifPresent(codec -> codec.decode(dataType, eleType));

                    return dataType;
                })
                .ifPresent(type::setElementType);

        return type;
    }

    @Override
    protected void doEncode(Map<String, Object> encoded, ArrayType type) {
        super.doEncode(encoded, type);
        IotGatewayDataTypeCodecs.getCodec(type.getElementType().getId())
                .ifPresent(codec -> encoded.put("elementType", codec.encode(type.getElementType())));

    }
}
