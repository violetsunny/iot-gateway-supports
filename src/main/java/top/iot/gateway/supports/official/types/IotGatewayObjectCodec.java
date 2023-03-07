package top.iot.gateway.supports.official.types;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.PropertyMetadata;
import top.iot.gateway.core.metadata.types.ObjectType;
import top.iot.gateway.supports.official.IotGatewayPropertyMetadata;

import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Getter
@Setter
public class IotGatewayObjectCodec extends AbstractDataTypeCodec<ObjectType> {

    @Override
    public String getTypeId() {
        return ObjectType.ID;
    }

    @Override
    public ObjectType decode(ObjectType type, Map<String, Object> config) {
        super.decode(type, config);
        JSONObject jsonObject = new JSONObject(config);

        ofNullable(jsonObject.getJSONArray("properties"))
                .map(list -> list
                        .stream()
                        .map(JSON::toJSON)
                        .map(JSONObject.class::cast)
                        .<PropertyMetadata>map(IotGatewayPropertyMetadata::new)
                        .collect(Collectors.toList()))
                .ifPresent(type::setProperties);


        return type;
    }

    @Override
    protected void doEncode(Map<String, Object> encoded, ObjectType type) {
        super.doEncode(encoded, type);
        if (type.getProperties() != null) {
            encoded.put("properties", type
                    .getProperties()
                    .stream()
                    .map(IotGatewayPropertyMetadata::new)
                    .map(PropertyMetadata::toJson)
                    .collect(Collectors.toList()));
        }

    }
}
