package top.iot.gateway.supports.cluster;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.iot.gateway.core.cluster.ClusterCache;
import top.iot.gateway.core.cluster.ClusterManager;
import top.iot.gateway.core.codec.Codecs;
import top.iot.gateway.core.codec.Encoder;
import top.iot.gateway.core.event.EventBus;
import reactor.core.publisher.Mono;

import java.util.Collection;

public class EventBusLocalCache<K, V> extends AbstractLocalCache<K, V> {

    private final EventBus eventBus;

    private final String topicPrefix;

    private static final byte notifyData = (byte) 1;
    private static final Encoder<Byte> encoder = Codecs.lookup(byte.class);


    public EventBusLocalCache(String name,
                              EventBus eventBus,
                              ClusterManager clusterManager) {
        this(name,
             eventBus,
             clusterManager,
             CacheBuilder.newBuilder().build());
    }

    public EventBusLocalCache(String name,
                              EventBus eventBus,
                              ClusterManager clusterManager,
                              Cache<K, Object> localCache) {
        this(name, eventBus, clusterManager.getCache(name), localCache);
    }

    public EventBusLocalCache(String name,
                              EventBus eventBus,
                              ClusterCache<K, V> clusterCache,
                              Cache<K, Object> localCache) {
        this(name, eventBus, clusterCache, localCache, true);
    }

    public EventBusLocalCache(String name,
                              EventBus eventBus,
                              ClusterCache<K, V> clusterCache,
                              Cache<K, Object> localCache,
                              boolean cacheEmpty) {
        super(clusterCache, localCache, cacheEmpty);
        this.eventBus = eventBus;
        this.topicPrefix = "/_sys/cluster_cache/" + name;
    }

    @Override
    protected Mono<Void> onUpdate(K key, V value) {
        return eventBus
                .publish(topicPrefix + "/update/" + key, encoder, notifyData)
                .then();
    }

    @Override
    protected Mono<Void> onRemove(K key) {
        return eventBus
                .publish(topicPrefix + "/remove/" + key, encoder, notifyData)
                .then();
    }

    @Override
    protected Mono<Void> onRemove(Collection<? extends K> key) {
        return eventBus
                .publish(topicPrefix + "/remove/__all", encoder, notifyData)
                .then();
    }

    @Override
    protected Mono<Void> onClear() {
        return eventBus
                .publish(topicPrefix + "/remove/__all", encoder, notifyData)
                .then();
    }
}
