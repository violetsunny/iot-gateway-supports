package top.iot.gateway.supports.official;

import lombok.SneakyThrows;
import top.iot.gateway.core.metadata.*;
import top.iot.gateway.core.metadata.types.BooleanType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class IotGatewayDeviceMetadataCodecTest {

    @Test
    @SneakyThrows
    public void test() {
        String metadata = StreamUtils.copyToString(new ClassPathResource("gateway.metadata.json").getInputStream(), StandardCharsets.UTF_8);

        IotGatewayDeviceMetadataCodec metadataCodec = new IotGatewayDeviceMetadataCodec();
        DeviceMetadata deviceMetadata = metadataCodec.decode(metadata).block();
        assertNotNull(deviceMetadata);
        Assert.assertTrue(deviceMetadata.getEvent("fire_alarm").isPresent());

        EventMetadata eventMetadata = deviceMetadata.getEvent("fire_alarm").get();

        assertEquals("object", eventMetadata.getType().getId());

        Assert.assertTrue(deviceMetadata.getFunction("playVoice").isPresent());

        DataType type= deviceMetadata.getFunction("playVoice").map(FunctionMetadata::getOutput).get();

        Assert.assertTrue(type instanceof BooleanType);

        PropertyMetadata prop = deviceMetadata.getPropertyOrNull("name");

        assertNotNull(prop);
        assertNotNull(prop.getValueType().getExpand("maxLength"));

    }
}