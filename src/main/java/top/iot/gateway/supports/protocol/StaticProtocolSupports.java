package top.iot.gateway.supports.protocol;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.ProtocolSupport;
import top.iot.gateway.core.ProtocolSupports;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticProtocolSupports implements ProtocolSupports {

    protected Map<String, ProtocolSupport> supports = new ConcurrentHashMap<>();

    @Override
    public boolean isSupport(String protocol) {
        return supports.containsKey(protocol);
    }

    @Override
    public Mono<ProtocolSupport> getProtocol(String protocol) {
        ProtocolSupport support = supports.get(protocol);
        if (support == null) {
            return Mono.error(new UnsupportedOperationException("不支持的协议:" + protocol));
        }
        return Mono.just(support);
    }

    @Override
    public Flux<ProtocolSupport> getProtocols() {
        return Flux.fromIterable(supports.values());
    }

    public void register(ProtocolSupport support) {
        ProtocolSupport old = supports.put(support.getId(), support);
        if (null != old) {
            old.dispose();
        }
    }

    public void unRegister(ProtocolSupport support) {
        unRegister(support.getId());
    }

    public void unRegister(String id) {
        ProtocolSupport old = supports.remove(id);
        if (null != old) {
            old.dispose();
        }
    }
}
