package top.iot.gateway.supports.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jctools.maps.NonBlockingHashMap;
import top.iot.gateway.core.cluster.ClusterManager;
import top.iot.gateway.core.config.ConfigStorage;
import top.iot.gateway.core.config.ConfigStorageManager;
import top.iot.gateway.core.event.EventBus;
import top.iot.gateway.core.event.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class EventBusStorageManager implements ConfigStorageManager, Disposable {

    static final String NOTIFY_TOPIC = "/_sys/cluster_cache";
    private static final AtomicReferenceFieldUpdater<EventBusStorageManager, Disposable>
            CLUSTER_SUBSCRIBER = AtomicReferenceFieldUpdater.newUpdater(
            EventBusStorageManager.class, Disposable.class, "disposable"
    );
    final ConcurrentMap<String, LocalCacheClusterConfigStorage> cache;

    private volatile Disposable disposable;

    private final EventBus eventBus;
    private final Function<String, LocalCacheClusterConfigStorage> storageBuilder;

    @Deprecated
    public EventBusStorageManager(ClusterManager clusterManager,
                                  EventBus eventBus) {

        this(clusterManager, eventBus, () -> CacheBuilder.newBuilder().build());
    }

    @Deprecated
    public EventBusStorageManager(ClusterManager clusterManager,
                                  EventBus eventBus,
                                  Supplier<Cache<String, Object>> supplier) {
        this(clusterManager, eventBus, supplier, true);
    }

    @Deprecated
    public EventBusStorageManager(ClusterManager clusterManager,
                                  EventBus eventBus,
                                  Supplier<Cache<String, Object>> supplier,
                                  boolean cacheEmpty) {
        this(clusterManager, eventBus, -1);
    }

    @SuppressWarnings("all")
    public EventBusStorageManager(ClusterManager clusterManager,
                                  EventBus eventBus,
                                  long ttl) {
        this.cache = new NonBlockingHashMap<>();
        this.eventBus = eventBus;
        storageBuilder = id -> {
            return new LocalCacheClusterConfigStorage(
                    id,
                    eventBus,
                    clusterManager.createCache(id),
                    ttl,
                    () -> {
                        this.cache.remove(id);
                    });
        };
    }

    private Disposable subscribeCluster() {
        return eventBus
                .subscribe(
                        Subscription
                                .builder()
                                .subscriberId("event-bus-storage-listener")
                                .topics(NOTIFY_TOPIC)
                                .justBroker()
                                .build(),
                        (topicPayload -> {
                            try {
                                CacheNotify cacheNotify = (CacheNotify) topicPayload.decode();
                                LocalCacheClusterConfigStorage storage = cache.get(cacheNotify.getName());
                                if (storage != null) {
                                    log.trace("clear local cache :{}", cacheNotify);
                                    storage.clearLocalCache(cacheNotify);
                                } else {
                                    log.trace("ignore clear local cache :{}", cacheNotify);
                                }
                            } catch (Throwable error) {
                                log.warn("clear local cache error", error);
                            }
                            return Mono.empty();
                        })
                );
    }

    @Override
    public void dispose() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    @SneakyThrows
    public Mono<ConfigStorage> getStorage(String id) {
        if (disposable == null) {
            synchronized (this) {
                Disposable disp = subscribeCluster();
                if (!CLUSTER_SUBSCRIBER.compareAndSet(this, null, disp)) {
                    disp.dispose();
                }
            }
        }
        return Mono.fromSupplier(() -> cache.computeIfAbsent(id, storageBuilder));
    }

}
