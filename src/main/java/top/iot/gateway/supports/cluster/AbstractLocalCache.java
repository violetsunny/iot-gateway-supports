package top.iot.gateway.supports.cluster;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import top.iot.gateway.core.cluster.ClusterCache;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractLocalCache<K, V> implements ClusterCache<K, V> {

    private final Cache<K, Object> cache;

    private final ClusterCache<K, V> clusterCache;

    private final boolean cacheEmpty;

    public AbstractLocalCache(ClusterCache<K, V> clusterCache,
                              Cache<K, Object> localCache,
                              boolean cacheEmpty) {
        this.cache = localCache;
        this.clusterCache = clusterCache;
        this.cacheEmpty = cacheEmpty;
    }

    public AbstractLocalCache(ClusterCache<K, V> clusterCache,
                              Cache<K, Object> localCache) {
        this(clusterCache, localCache, true);
    }


    public void clearLocalCache(K key) {
        if ("__all".equals(key)) {
            clearAllLocalCache();
        } else if (key != null) {
            cache.invalidate(key);
        }
    }

    public void clearAllLocalCache() {
        cache.invalidateAll();
    }

    protected abstract Mono<Void> onUpdate(K key, V value);

    protected abstract Mono<Void> onRemove(K key);

    protected abstract Mono<Void> onRemove(Collection<? extends K> key);

    protected abstract Mono<Void> onClear();

    public static final Object NULL_VALUE = new Object();

    @Override
    public Mono<V> get(K key) {
        if (StringUtils.isEmpty(key)) {
            return Mono.empty();
        }
        Object val = cache.getIfPresent(key);
        if (val != null) {
            return NULL_VALUE == val ? Mono.empty() : Mono.just((V) val);
        }
        return clusterCache
                .get(key)
                .switchIfEmpty(cacheEmpty ? Mono.fromRunnable(() -> cache.put(key, NULL_VALUE)) : Mono.empty())
                .doOnNext(v -> cache.put(key, v));
    }

    @AllArgsConstructor
    class SimpleEntry implements Map.Entry<K, V> {

        private final Map.Entry<K, Object> entry;

        @Override
        public K getKey() {
            return entry.getKey();
        }

        @Override
        public V getValue() {
            Object v = entry.getValue();
            return v == NULL_VALUE ? null : (V) v;
        }

        @Override
        public V setValue(V value) {
            Object old = getValue();
            entry.setValue(value);
            return (V) old;
        }
    }

    @Override
    public Flux<Map.Entry<K, V>> get(Collection<K> key) {
        if (CollectionUtils.isEmpty(key)) {
            return Flux.empty();
        }
        ImmutableMap<K, Object> all = cache.getAllPresent(key);
        if (key.size() == all.size()) {
            return Flux.fromIterable(all.entrySet()).map(SimpleEntry::new);
        }
        return clusterCache
                .get(key)
                .doOnNext(kvEntry -> {
                    K k = kvEntry.getKey();
                    V v = kvEntry.getValue();
                    if (v == null) {
                        if (cacheEmpty) {
                            cache.put(k, NULL_VALUE);
                        }
                    } else {
                        cache.put(k, v);
                    }
                });
    }

    @Override
    public Mono<Boolean> put(K key, V value) {
        if (key == null) {
            return Mono.just(true);
        }
        if (value == null) {
            return remove(key);
        }
        return Mono
                .defer(() -> {
                    cache.put(key, value);
                    return clusterCache
                            .put(key, value)
                            .flatMap(r -> onUpdate(key, value))
                            .thenReturn(true);
                });
    }

    @Override
    public Mono<Boolean> putIfAbsent(K key, V value) {
        if (key == null) {
            return Mono.just(true);
        }
        if (value == null) {
            return remove(key);
        }
        return Mono
                .defer(() -> {
                    cache.invalidate(key);
                    return clusterCache
                            .putIfAbsent(key, value)
                            .flatMap(r -> onUpdate(key, value).thenReturn(r));
                });
    }

    @Override
    public Mono<Boolean> remove(K key) {
        if (key == null) {
            return Mono.just(true);
        }
        return Mono.defer(() -> {
            cache.invalidate(key);
            return clusterCache
                    .remove(key)
                    .flatMap(r -> onRemove(key))
                    .thenReturn(true);
        });
    }

    @Override
    public Mono<V> getAndRemove(K key) {
        if (key == null) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            cache.invalidate(key);
            return clusterCache
                    .getAndRemove(key)
                    .flatMap(r -> onRemove(key).thenReturn(r));
        });
    }

    @Override
    public Mono<Boolean> remove(Collection<K> key) {
        if (key == null) {
            return Mono.just(true);
        }
        return Mono.defer(() -> {
            cache.invalidateAll(key);
            return clusterCache
                    .remove(key)
                    .flatMap(r -> onRemove(key))
                    .thenReturn(true);
        });
    }

    @Override
    public Mono<Boolean> containsKey(K key) {
        if (key == null) {
            return Mono.just(true);
        }
        return Mono.defer(() -> {
            if (clusterCache.containsKey(key) != null) {
                return Mono.just(true);
            }
            return clusterCache.containsKey(key);
        });
    }

    @Override
    public Flux<K> keys() {
        return clusterCache.keys();
    }

    @Override
    public Flux<V> values() {
        return clusterCache.values();
    }

    @Override
    public Mono<Boolean> putAll(Map<? extends K, ? extends V> multi) {
        if (CollectionUtils.isEmpty(multi)) {
            return Mono.just(true);
        }
        return Mono.defer(() -> {
            List<K> remove = multi.entrySet()
                                  .stream()
                                  .filter(e -> e.getValue() == null)
                                  .map(Map.Entry::getKey)
                                  .collect(Collectors.toList());
            Map<? extends K, ? extends V> newTarget = new HashMap<>(multi);

            if (remove.size() > 0) {
                cache.invalidateAll(remove);
                remove.forEach(newTarget::remove);
            }
            cache.putAll(newTarget);
            return clusterCache
                    .putAll(multi)
                    .then(onRemove(multi.keySet()))
                    .thenReturn(true);
        });
    }

    @Override
    public Mono<Integer> size() {
        return clusterCache.size();
    }

    @Override
    public Flux<Map.Entry<K, V>> entries() {
        return clusterCache.entries();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.defer(() -> {
            cache.invalidateAll();
            return clusterCache
                    .clear()
                    .then(onClear());
        });
    }

    @Override
    public Mono<Void> refresh(Collection<? extends K> keys) {
        if (null != keys) {
            cache.invalidateAll(keys);
            if (keys.size() == 1) {
                return onRemove(keys.iterator().next());
            }
            return onRemove(keys);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> refresh() {
        //清空本地缓存
        cache.invalidateAll();
        //发送清空事件,通知其他节点也清空
        return onClear();
    }
}
