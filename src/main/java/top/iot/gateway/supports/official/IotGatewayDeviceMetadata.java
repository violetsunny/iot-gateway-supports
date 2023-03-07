package top.iot.gateway.supports.official;

import com.alibaba.fastjson.JSONObject;
import top.iot.gateway.core.metadata.DeviceMetadata;
import top.iot.gateway.core.metadata.MergeOption;
import top.iot.gateway.core.things.ThingMetadata;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class IotGatewayDeviceMetadata extends DefaultThingsMetadata implements DeviceMetadata {

    public IotGatewayDeviceMetadata(String id, String name) {
        super(id,name);
    }

    public IotGatewayDeviceMetadata(JSONObject jsonObject) {
       super(jsonObject);
    }

    public IotGatewayDeviceMetadata(DeviceMetadata another) {
       super(another);
    }

    @Override
    protected DefaultThingsMetadata copy() {
        return new IotGatewayDeviceMetadata(this);
    }

    @Override
    public <T extends ThingMetadata> DeviceMetadata merge(T metadata, MergeOption... options) {
        return (DeviceMetadata)super.merge(metadata, options);
    }
}
