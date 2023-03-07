package top.iot.gateway.supports.protocol.codec.defaults;

import lombok.AllArgsConstructor;
import top.iot.gateway.supports.protocol.codec.BinaryCodec;
import top.iot.gateway.supports.protocol.codec.Endian;

@AllArgsConstructor(staticName = "of")
public class Ieee754DoubleCodec implements BinaryCodec<Double> {

    private final Endian endian;

    private final int offset;

    @Override
    public Double decode(byte[] payload, int offset) {
        return Double.longBitsToDouble((int) endian.decode(payload, this.offset + offset, 8));
    }

    @Override
    public void encode(Double part, byte[] payload, int offset) {
        endian.encode(Double.doubleToLongBits(part), payload, this.offset + offset, 8);
    }
}
