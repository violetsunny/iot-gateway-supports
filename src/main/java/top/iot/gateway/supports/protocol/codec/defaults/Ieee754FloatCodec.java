package top.iot.gateway.supports.protocol.codec.defaults;

import lombok.AllArgsConstructor;
import top.iot.gateway.supports.protocol.codec.BinaryCodec;
import top.iot.gateway.supports.protocol.codec.Endian;

@AllArgsConstructor(staticName = "of")
public class Ieee754FloatCodec implements BinaryCodec<Float> {

    private final Endian endian;

    private final int offset;

    @Override
    public Float decode(byte[] payload, int offset) {
        return Float.intBitsToFloat((int) endian.decode(payload, this.offset + offset, 4));
    }

    @Override
    public void encode(Float part, byte[] payload, int offset) {
        endian.encode(Float.floatToIntBits(part), payload, this.offset + offset, 4);
    }
}
