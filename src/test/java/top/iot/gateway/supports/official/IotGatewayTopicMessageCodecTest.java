package top.iot.gateway.supports.official;

import top.iot.gateway.core.message.property.ReadPropertyMessage;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class IotGatewayTopicMessageCodecTest {

    IotGatewayTopicMessageCodec codec = new IotGatewayTopicMessageCodec();

    @Test
    public void testReadProperty() {

        ReadPropertyMessage readProperty = new ReadPropertyMessage();
        readProperty.setProperties(Arrays.asList("name"));
        readProperty.setMessageId("test");
        IotGatewayTopicMessageCodec.EncodedTopic topic = codec.encode("test", readProperty);
        assertEquals(topic.getTopic(),"/test/properties/read");

    }

}