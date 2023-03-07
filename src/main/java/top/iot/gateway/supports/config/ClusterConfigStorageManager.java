package top.iot.gateway.supports.config;

import top.iot.gateway.core.cluster.ClusterManager;
import top.iot.gateway.core.config.ConfigStorage;
import top.iot.gateway.core.config.ConfigStorageManager;
import top.iot.gateway.supports.cluster.ClusterLocalCache;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public class ClusterConfigStorageManager implements ConfigStorageManager {

    private final ClusterManager clusterManager;

    private final Map<String, ClusterConfigStorage> storageMap = new ConcurrentHashMap<>();

    @SuppressWarnings("all")
    public ClusterConfigStorageManager(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
        clusterManager
                .getTopic("_local_cache_modify:*")
                .subscribePattern()
                .subscribe(msg -> {
                    String[] type = msg.getTopic().split("[:]", 2);
                    if (type.length <= 0) {
                        return;
                    }
                    Optional.ofNullable(storageMap.get(type[1]))
                            .ifPresent(store -> ((ClusterLocalCache) store.getCache()).clearLocalCache(msg.getMessage()));

                });
    }

    @Override
    public Mono<ConfigStorage> getStorage(String id) {
        return Mono.fromSupplier(() -> storageMap.computeIfAbsent(id, __ -> new ClusterConfigStorage(new ClusterLocalCache<>(id, clusterManager))));
    }
}
