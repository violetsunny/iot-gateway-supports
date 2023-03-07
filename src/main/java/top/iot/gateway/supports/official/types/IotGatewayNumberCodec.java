package top.iot.gateway.supports.official.types;

import com.alibaba.fastjson.JSONObject;
import top.iot.gateway.core.metadata.types.NumberType;
import top.iot.gateway.core.metadata.unit.ValueUnits;

import java.math.RoundingMode;
import java.util.Map;

import static java.util.Optional.ofNullable;

public abstract class IotGatewayNumberCodec<T extends NumberType<?>> extends AbstractDataTypeCodec<T>{
    @Override
    public abstract String getTypeId();

    @Override
    public T decode(T type, Map<String, Object> config) {
        super.decode(type,config);
        JSONObject jsonObject = new JSONObject(config);
        ofNullable(jsonObject.getDouble("max"))
                .ifPresent(type::setMax);
        ofNullable(jsonObject.getDouble("min"))
                .ifPresent(type::setMin);
        ofNullable(jsonObject.getInteger("scale"))
                .ifPresent(type::setScale);
        ofNullable(jsonObject.getString("unit"))
                .flatMap(ValueUnits::lookup)
                .ifPresent(type::setUnit);
        ofNullable(jsonObject.getString("round"))
                .map(RoundingMode::valueOf)
                .ifPresent(type::setRound);
        return type;
    }

    @Override
    protected void doEncode(Map<String, Object> encoded, T type) {
        encoded.put("max", type.getMax());
        encoded.put("min", type.getMin());
        encoded.put("scale", type.getScale());
        encoded.put("round", type.getRound().name());
        if (type.getUnit() != null) {
            encoded.put("unit", type.getUnit().getId());
        }
    }

}
