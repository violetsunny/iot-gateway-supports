package top.iot.gateway.supports.official;

import top.iot.gateway.core.metadata.DeviceMetadata;
import top.iot.gateway.core.metadata.types.StringType;
import org.junit.Test;

import static org.junit.Assert.*;

public class IotGatewayDeviceMetadataTest {

    @Test
    public void testProperties(){
        IotGatewayDeviceMetadata metadata = new IotGatewayDeviceMetadata("test","test");

        metadata.addProperty(new IotGatewayPropertyMetadata("test", "test", StringType.GLOBAL));
        metadata.addProperty(new IotGatewayPropertyMetadata("test2", "test2", StringType.GLOBAL));


        assertEquals(2,metadata.getProperties().size());

        {
            IotGatewayDeviceMetadata newMetadata = new IotGatewayDeviceMetadata(metadata);
            assertEquals(2,newMetadata.getProperties().size());
        }

        {
            DeviceMetadata newMetadata = new IotGatewayDeviceMetadata("test", "test").merge(metadata);

            assertEquals(2,newMetadata.getProperties().size());
        }

    }
}