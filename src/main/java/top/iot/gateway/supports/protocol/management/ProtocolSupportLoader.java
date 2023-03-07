package top.iot.gateway.supports.protocol.management;

import top.iot.gateway.core.ProtocolSupport;
import reactor.core.publisher.Mono;

public interface ProtocolSupportLoader {

    Mono<? extends ProtocolSupport> load(ProtocolSupportDefinition definition);
}
