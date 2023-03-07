package top.iot.gateway.supports.official;

import top.iot.gateway.core.metadata.DataType;
import top.iot.gateway.core.metadata.DataTypeCodec;
import top.iot.gateway.supports.official.types.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IotGatewayDataTypeCodecs {

    private static final Map<String, DataTypeCodec<? extends DataType>> codecMap = new HashMap<>();

    static {
        register(new IotGatewayArrayCodec());
        register(new IotGatewayBooleanCodec());
        register(new IotGatewayDateCodec());
        register(new IotGatewayDoubleCodec());
        register(new IotGatewayEnumCodec());
        register(new IotGatewayFloatCodec());
        register(new IotGatewayGeoPointCodec());
        register(new IotGatewayIntCodec());
        register(new IotGatewayLongCodec());
        register(new IotGatewayObjectCodec());
        register(new IotGatewayStringCodec());
        register(new IotGatewayPasswordCodec());
        register(new IotGatewayFileCodec());
        register(new IotGatewayGeoShapeCodec());
        register(new IotGatewayUserCodec());
    }

    public static void register(DataTypeCodec<? extends DataType> codec) {
        codecMap.put(codec.getTypeId(), codec);
    }

    @SuppressWarnings("all")
    public static Optional<DataTypeCodec<DataType>> getCodec(String typeId) {

        return Optional.ofNullable((DataTypeCodec) codecMap.get(typeId));
    }

    public static DataType decode(DataType type, Map<String, Object> config) {
        if (type == null) {
            return null;
        }
        return getCodec(type.getId())
                .map(codec -> codec.decode(type, config))
                .orElse(type);
    }

    public static Optional<Map<String, Object>> encode(DataType type) {
        if (type == null) {
            return Optional.empty();
        }
        return getCodec(type.getId())
                .map(codec -> codec.encode(type));
    }
}
