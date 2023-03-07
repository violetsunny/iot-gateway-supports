package top.iot.gateway.supports.protocol.codec.defaults;

import top.iot.gateway.supports.protocol.codec.BinaryDecoder;

import java.util.Map;

public interface MapDecoderBuilder<K, V> {

    MapDecoderBuilder<K, V> add(BinaryDecoder<? extends K> keyDecoder,
                                BinaryDecoder<? extends V> valueDecoder);

    BinaryDecoder<Map<K, V>> build();
}
