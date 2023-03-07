package top.iot.gateway.supports.protocol.codec;


import top.iot.gateway.core.message.DeviceMessage;

/**
 *
 * @see BlockingDecoderBuilder
 */
public interface BlockingDecoder {

    static BlockingDecoderBuilder.BlockingDecoderDeclaration declare() {
        return new DefaultBlockingDecoderBuilder().declare();
    }

    DeviceMessage decode(byte[] message, int offset);

}
