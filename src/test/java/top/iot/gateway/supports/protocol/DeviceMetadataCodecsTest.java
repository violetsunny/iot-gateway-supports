package top.iot.gateway.supports.protocol;

import top.iot.gateway.core.metadata.DeviceMetadataCodecs;
import org.junit.Assert;
import org.junit.Test;

public class DeviceMetadataCodecsTest {

    @Test
    public void test(){
        Assert.assertNotNull(DeviceMetadataCodecs.defaultCodec());
    }
}
