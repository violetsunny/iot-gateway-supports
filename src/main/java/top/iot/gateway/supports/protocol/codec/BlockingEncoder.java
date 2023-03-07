package top.iot.gateway.supports.protocol.codec;

import top.iot.gateway.core.message.codec.EncodedMessage;
import top.iot.gateway.core.message.codec.MessageEncodeContext;

public interface BlockingEncoder {

    EncodedMessage encode(MessageEncodeContext context);

}
