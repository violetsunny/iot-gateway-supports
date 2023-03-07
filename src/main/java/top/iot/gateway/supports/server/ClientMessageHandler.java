package top.iot.gateway.supports.server;

import top.iot.gateway.core.device.DeviceOperator;
import top.iot.gateway.core.message.codec.MessageDecodeContext;
import top.iot.gateway.core.message.codec.Transport;
import reactor.core.publisher.Mono;

public interface ClientMessageHandler {
    Mono<Boolean> handleMessage(DeviceOperator operator, Transport transport, MessageDecodeContext message);

}
