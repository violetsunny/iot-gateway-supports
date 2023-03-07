package top.iot.gateway.supports.server;

import lombok.SneakyThrows;
import top.iot.gateway.core.device.*;
import top.iot.gateway.core.message.ChildDeviceMessageReply;
import top.iot.gateway.core.message.property.ReadPropertyMessageReply;
import top.iot.gateway.core.server.monitor.GatewayServerMetrics;
import top.iot.gateway.core.server.monitor.GatewayServerMonitor;
import top.iot.gateway.supports.TestDeviceSession;
import top.iot.gateway.supports.protocol.ServiceLoaderProtocolSupports;
import top.iot.gateway.supports.server.monitor.MicrometerGatewayServerMetrics;
import top.iot.gateway.supports.server.session.DefaultDeviceSessionManager;
import top.iot.gateway.supports.server.session.TestDeviceRegistry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import reactor.test.StepVerifier;

@Ignore
public class DefaultSendToDeviceMessageHandlerTest {
    private StandaloneDeviceMessageBroker broker;

    private DefaultDeviceSessionManager sessionManager;

    private TestDeviceRegistry registry;

    private DefaultSendToDeviceMessageHandler handler;

    private DefaultDecodedClientMessageHandler decodedHandler;

    @Before
    public void init() {
        sessionManager = new DefaultDeviceSessionManager();
        sessionManager.setGatewayServerMonitor(new GatewayServerMonitor() {
            @Override
            public String getCurrentServerId() {
                return "test";
            }

            @Override
            public GatewayServerMetrics metrics() {
                return new MicrometerGatewayServerMetrics(getCurrentServerId());
            }
        });
        broker = new StandaloneDeviceMessageBroker();

        ServiceLoaderProtocolSupports supports = new ServiceLoaderProtocolSupports();
        supports.init();
        registry = new TestDeviceRegistry(supports, broker);

        sessionManager.setRegistry(registry);

        decodedHandler = new DefaultDecodedClientMessageHandler(broker, sessionManager);

        handler = new DefaultSendToDeviceMessageHandler("test", sessionManager, broker, registry, decodedHandler);
        sessionManager.init();
        handler.startup();
    }

    @Test
    @SneakyThrows
    public void testMessage() {
        registry.register(ProductInfo
                                  .builder()
                                  .id("test")
                                  .protocol("jetlinks.v1.0")
                                  .build())
                .block();;

        DeviceOperator device = registry
                .register(DeviceInfo.builder()
                                    .id("test")
                                    .productId("test")
                                    .build())
                .block();

        DeviceOperator children = registry
                .register(DeviceInfo.builder()
                                    .id("test-children")
                                    .productId("test")
                                    .build())
                .block();

        children.setConfig(DeviceConfigKey.parentGatewayId, "test").block();

        sessionManager.register(new TestDeviceSession("test", device, message -> {

            ReadPropertyMessageReply readPropertyMessageReply = new ReadPropertyMessageReply();
            readPropertyMessageReply.setMessageId("test");
            readPropertyMessageReply.setSuccess(true);

            ChildDeviceMessageReply reply = new ChildDeviceMessageReply();
            reply.setMessageId(readPropertyMessageReply.getMessageId());
            reply.setChildDeviceId("test-children");
            reply.setChildDeviceMessage(readPropertyMessageReply);

            decodedHandler.handleMessage(sessionManager.getSession("test").getOperator(), reply)
                          .subscribe();
        }));

        sessionManager.registerChildren("test", "test-children").block();


        Thread.sleep(1);
        children.messageSender()
                .readProperty("name")
                .messageId("test")
                .send()
                .map(ReadPropertyMessageReply::isSuccess)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();


    }

}