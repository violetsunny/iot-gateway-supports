package top.iot.gateway.supports.config;

import lombok.SneakyThrows;
import top.iot.gateway.core.cluster.ClusterManager;
import top.iot.gateway.core.config.ConfigStorage;
import top.iot.gateway.core.config.ConfigStorageManager;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@Deprecated
public class SimpleConfigStorageManager implements ConfigStorageManager {

    private final Function<String, ClusterConfigStorage> storageBuilder;
    private final ConcurrentMap<String, ClusterConfigStorage> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("all")
    public SimpleConfigStorageManager(ClusterManager clusterManager) {
        storageBuilder = id -> {
            return new ClusterConfigStorage(clusterManager.getCache(id));
        };
    }

    @Override
    @SneakyThrows
    public Mono<ConfigStorage> getStorage(String id) {
        return Mono.just(cache.computeIfAbsent(id, storageBuilder));
    }
}
