package top.iot.gateway.supports.official;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.metadata.*;
import top.iot.gateway.core.metadata.types.DataTypes;
import top.iot.gateway.core.metadata.types.UnknownType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class IotGatewayEventMetadata implements EventMetadata {

    private JSONObject jsonObject;

    private DataType type;

    private transient EventMetadata another;

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private Map<String, Object> expands;

    public IotGatewayEventMetadata(String id, String name, DataType type) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public IotGatewayEventMetadata(JSONObject jsonObject) {
        fromJson(jsonObject);
    }

    public IotGatewayEventMetadata(EventMetadata another) {
        this.another = another;
        this.id = another.getId();
        this.name = another.getName();
        this.description = another.getDescription();
        this.expands = another.getExpands();
    }

    @Override
    public DataType getType() {
        if (type == null && jsonObject != null) {
            JSONObject typeJson = jsonObject.getJSONObject("valueType");

            type = Optional.ofNullable(typeJson.getString("type"))
                           .map(DataTypes::lookup)
                           .map(Supplier::get)
                           .orElseGet(UnknownType::new);

            type = IotGatewayDataTypeCodecs.decode(type, typeJson);
        }
        if (type == null && another != null) {
            type = another.getType();
        }
        return type;
    }

    @Override
    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id);
        jsonObject.put("name", name);
        jsonObject.put("description", description);
        jsonObject.put("valueType", IotGatewayDataTypeCodecs.encode(getType()).orElse(null));
        jsonObject.put("expands", expands);
        return jsonObject;
    }

    @Override
    public void fromJson(JSONObject json) {
        this.jsonObject = json;
        this.type = null;
        this.id = json.getString("id");
        this.name = json.getString("name");
        this.description = json.getString("description");
        this.expands = json.getJSONObject("expands");

    }

    @Override
    public EventMetadata merge(EventMetadata another, MergeOption... option) {
        IotGatewayEventMetadata metadata = new IotGatewayEventMetadata(this);
        if (metadata.expands == null) {
            metadata.expands = new HashMap<>();
        }

        MergeOption.ExpandsMerge.doWith(DeviceMetadataType.event, another.getExpands(), metadata.expands, option);

        return metadata;
    }
}
