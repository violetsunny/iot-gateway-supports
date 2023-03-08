package top.iot.gateway.supports.official;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.defaults.CompositeProtocolSupport;
import top.iot.gateway.core.message.codec.DefaultTransport;
import top.iot.gateway.core.metadata.*;
import top.iot.gateway.core.metadata.types.*;
import top.iot.gateway.core.spi.*;

@Deprecated // https://github.com/iot-gateway/iot-gateway-official-protocol
@Slf4j
public class IotGatewayProtocolSupportProvider implements ProtocolSupportProvider {

    private static final DefaultConfigMetadata mqttConfig = new DefaultConfigMetadata(
            "MQTT认证配置"
            , "MQTT认证时需要的配置,mqtt用户名,密码算法:\n" +
            "username=secureId|timestamp\n" +
            "password=md5(secureId|timestamp|secureKey)\n" +
            "\n" +
            "timestamp为时间戳,与服务时间不能相差5分钟")
            .add("secureId", "secureId", "密钥ID", new StringType())
            .add("secureKey", "secureKey", "密钥KEY", new PasswordType());

    private static final DefaultConfigMetadata coapConfig = new DefaultConfigMetadata(
            "CoAP认证配置",
            "使用CoAP进行数据上报时,需要对数据进行加密:" +
                    "encrypt(payload,secureKey);")
            .add("encAlg", "加密算法", "加密算法",
                    new EnumType().addElement(EnumType.Element.of("AES", "AES加密(ECB,PKCS#5)", "加密模式:ECB,填充方式:PKCS#5")))
            .add("secureKey", "密钥", "16位密钥KEY", new PasswordType());

    private static final DefaultConfigMetadata coapDTLSConfig = new DefaultConfigMetadata(
            "CoAP DTLS配置",
            "使用CoAP DTLS 进行数据上报需要先进行签名认证获取token.\n" +
                    "之后上报数据需要在Option中携带token信息. \n" +
                    "自定义Option: 2110,sign ; 2111,token ")
            .add("secureKey", "密钥", "认证签名密钥", new PasswordType());


    @Override
    public Mono<CompositeProtocolSupport> create(ServiceContext context) {
        log.warn("内置官方协议支持即将弃用,请使用 https://github.com/iot-gateway/iot-gateway-official-protocol");
        return Mono.defer(() -> {
            CompositeProtocolSupport support = new CompositeProtocolSupport();

            support.setId("iot-gateway.v1.0");
            support.setName("iot-gateway V1.0");
            support.setDescription("iot-gateway Protocol Version 1.0");

            support.addAuthenticator(DefaultTransport.MQTT, new IotGatewayAuthenticator());
            support.addAuthenticator(DefaultTransport.MQTT_TLS, new IotGatewayAuthenticator());

            support.setMetadataCodec(new IotGatewayDeviceMetadataCodec());

            support.addConfigMetadata(DefaultTransport.MQTT, mqttConfig);
            support.addConfigMetadata(DefaultTransport.MQTT_TLS, mqttConfig);
            support.addConfigMetadata(DefaultTransport.CoAP, coapConfig);
            support.addConfigMetadata(DefaultTransport.CoAP_DTLS, coapDTLSConfig);

            support.addMessageCodecSupport(new IotGatewayMqttDeviceMessageCodec(DefaultTransport.MQTT));
            support.addMessageCodecSupport(new IotGatewayMqttDeviceMessageCodec(DefaultTransport.MQTT_TLS));

            support.addMessageCodecSupport(new IotGatewayCoapDeviceMessageCodec());
            support.addMessageCodecSupport(new IotGatewayCoapDTLSDeviceMessageCodec());


            return Mono.just(support);
        });
    }
}
