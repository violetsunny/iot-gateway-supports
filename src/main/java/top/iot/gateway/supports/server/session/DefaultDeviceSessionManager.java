package top.iot.gateway.supports.server.session;

import lombok.Getter;
import lombok.Setter;
import top.iot.gateway.core.device.DeviceRegistry;
import top.iot.gateway.core.message.codec.Transport;
import top.iot.gateway.core.server.monitor.GatewayServerMonitor;
import top.iot.gateway.core.server.session.ChildrenDeviceSession;
import top.iot.gateway.core.server.session.DeviceSession;
import top.iot.gateway.core.server.session.DeviceSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Deprecated
public class DefaultDeviceSessionManager implements DeviceSessionManager {

    private final Map<String, DeviceSession> repository = new ConcurrentHashMap<>(4096);

    private final Map<String, Map<String, ChildrenDeviceSession>> children = new ConcurrentHashMap<>(4096);

    @Getter
    @Setter
    private Logger log = LoggerFactory.getLogger(DefaultDeviceSessionManager.class);

    @Getter
    @Setter
    private GatewayServerMonitor gatewayServerMonitor;

    @Getter
    @Setter
    private ScheduledExecutorService executorService;

    @Getter
    @Setter
    private DeviceRegistry registry;

    private FluxProcessor<DeviceSession, DeviceSession> onDeviceRegister = EmitterProcessor.create(false);

    private FluxProcessor<DeviceSession, DeviceSession> onDeviceUnRegister = EmitterProcessor.create(false);

    private String serverId;

    private Queue<Runnable> scheduleJobQueue = new ArrayDeque<>();

    private Map<String, LongAdder> transportCounter = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private Map<String, Long> transportLimits = new ConcurrentHashMap<>();

    public void setTransportLimit(Transport transport, long limit) {
        transportLimits.put(transport.getId(), limit);
    }

    public void shutdown() {
        repository.values()
                .parallelStream()
                .map(DeviceSession::getId)
                .forEach(this::unregister);
    }

    @Override
    public boolean isOutOfMaximumSessionLimit(Transport transport) {
        long max = getMaximumSession(transport);
        return max > 0 && getCurrentSession(transport) >= max;
    }

    @Override
    public long getMaximumSession(Transport transport) {
        Long counter = transportLimits.get(transport.getId());
        return counter == null ? -1 : counter;
    }

    @Override
    public long getCurrentSession(Transport transport) {
        LongAdder counter = transportCounter.get(transport.getId());
        return counter == null ? 0 : counter.longValue();
    }

    public Mono<Long> checkSession() {
        AtomicLong startWith = new AtomicLong();
        return Flux.fromIterable(repository.values())
                .distinct()
                .publishOn(Schedulers.parallel())
                .filterWhen(session -> {
                    if (!session.isAlive()) {
                        return Mono.just(true);
                    }
                    return session
                            .getOperator()
                            .getConnectionServerId()
                            .switchIfEmpty(Mono.just(""))
                            .filter(s -> !serverId.equals(s))
                            .doOnNext((ignore) -> log.warn("device [{}] state error", session.getDeviceId()))
                            .flatMap(ignore -> session.getOperator().online(serverId, session.getId()))
                            .thenReturn(false);
                })
                .map(DeviceSession::getId)
                .doOnNext(this::unregister)
                .collect(Collectors.counting())
                .doOnNext((l) -> {
                    if (log.isInfoEnabled() && l > 0) {
                        log.info("expired sessions:{}", l);
                    }
                })
                .name("session_checker:".concat(serverId))
                .metrics()
                .doOnError(err -> log.error(err.getMessage(), err))
                .doOnSubscribe(subscription -> {
                    log.info("start check session");
                    startWith.set(System.currentTimeMillis());
                })
                .doFinally(s -> {
                    //??????session??????
                    transportCounter.forEach((transport, number) -> gatewayServerMonitor.metrics().reportSession(transport, number.intValue()));
                    //????????????
                    for (Runnable runnable = scheduleJobQueue.poll(); runnable != null; runnable = scheduleJobQueue.poll()) {
                        runnable.run();
                    }
                    if (log.isInfoEnabled()) {
                        log.info("check session complete,current server sessions:{}.use time:{}ms.",
                                transportCounter,
                                System.currentTimeMillis() - startWith.get());

                    }
                });
    }

    public void init() {
        Objects.requireNonNull(gatewayServerMonitor, "gatewayServerMonitor");
        Objects.requireNonNull(registry, "registry");
        if (executorService == null) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        serverId = gatewayServerMonitor.getCurrentServerId();

        //???30?????????????????????????????????
        executorService.scheduleAtFixedRate(() -> this.checkSession().subscribe(), 10, 30, TimeUnit.SECONDS);

    }


    @Override
    public DeviceSession getSession(String clientId) {
        DeviceSession session = repository.get(clientId);

        if (session == null || !session.isAlive()) {
            return null;
        }
        return session;
    }

    @Override
    public ChildrenDeviceSession getSession(String deviceId, String childrenId) {
        return Optional.ofNullable(children.get(deviceId))
                .map(map -> map.get(childrenId))
                .filter(ChildrenDeviceSession::isAlive)
                .orElse(null);
    }

    @Override
    public Mono<ChildrenDeviceSession> registerChildren(String deviceId, String childrenDeviceId) {
        return Mono.defer(() -> {
            DeviceSession session = getSession(deviceId);
            if (session == null) {
                log.warn("device[{}] session not alive", deviceId);
                return Mono.empty();
            }
            return registry
                    .getDevice(childrenDeviceId)
                    .switchIfEmpty(Mono.fromRunnable(() -> log.warn("children device [{}] not fond in registry", childrenDeviceId)))
                    .flatMap(deviceOperator -> deviceOperator
                            .online(session.getServerId().orElse(serverId), session.getId())
                            .thenReturn(new ChildrenDeviceSession(childrenDeviceId, session, deviceOperator)))
                    .doOnSuccess(s -> children.computeIfAbsent(deviceId, __ -> new ConcurrentHashMap<>()).put(childrenDeviceId, s));
        });

    }

    @Override
    public Mono<ChildrenDeviceSession> unRegisterChildren(String deviceId, String childrenId) {

        return Mono.justOrEmpty(children.get(deviceId))
                .flatMap(map -> Mono.justOrEmpty(map.remove(childrenId)))
                .doOnNext(ChildrenDeviceSession::close)
                .flatMap(session -> session.getOperator()
                        .offline()
                        .doFinally(s -> {
                            //??????
                            if (onDeviceRegister.hasDownstreams()) {
                                onDeviceRegister.onNext(session);
                            }
                        })
                        .thenReturn(session));
    }

    @Override
    public DeviceSession replace(DeviceSession oldSession, DeviceSession newSession) {
        DeviceSession old = repository.put(oldSession.getDeviceId(), newSession);
        if (old != null) {
            //??????sessionId??????
            if (!old.getId().equals(old.getDeviceId())) {
                repository.put(oldSession.getId(), newSession);
            }
        }
        return newSession;
    }

    @Override
    public DeviceSession register(DeviceSession session) {
        DeviceSession old = repository.put(session.getDeviceId(), session);
        if (old != null) {
            //??????sessionId??????
            if (!old.getId().equals(old.getDeviceId())) {
                repository.remove(old.getId());
            }
        }
        if (!session.getId().equals(session.getDeviceId())) {
            repository.put(session.getId(), session);
        }
        if (null != old) {
            //1. ???????????????????????????????????????id.
            //2. ????????????????????????,?????????????????????,??????????????????,??????????????????????????????????????????.
            log.warn("device[{}] session exists,disconnect old session:{}", old.getDeviceId(), session);
            //????????????????????????
            scheduleJobQueue.add(old::close);
        } else {
            //????????????
            transportCounter
                    .computeIfAbsent(session.getTransport().getId(), transport -> new LongAdder())
                    .increment();
        }

        //??????????????????
        session.getOperator()
                .online(session.getServerId().orElse(serverId), session.getId())
                .doFinally(s -> {
                    //??????
                    if (onDeviceRegister.hasDownstreams()) {
                        onDeviceRegister.onNext(session);
                    }
                })
                .subscribe();

        return old;
    }

    @Override
    public Flux<DeviceSession> onRegister() {
        return onDeviceRegister
                .map(Function.identity())
                .doOnError(err -> log.error(err.getMessage(), err));
    }

    @Override
    public Flux<DeviceSession> onUnRegister() {
        return onDeviceUnRegister
                .map(Function.identity())
                .doOnError(err -> log.error(err.getMessage(), err));
    }

    @Override
    public Flux<DeviceSession> getAllSession() {
        return Flux
                .fromIterable(repository.values())
                .distinct(DeviceSession::getDeviceId);
    }

    @Override
    public boolean sessionIsAlive(String deviceId) {
        return getSession(deviceId) != null
                ||
                children.values()
                        .stream()
                        .anyMatch(r -> {
                            DeviceSession session = r.get(deviceId);
                            return session != null && session.isAlive();
                        });
    }

    @Override
    public DeviceSession unregister(String idOrDeviceId) {
        DeviceSession session = repository.remove(idOrDeviceId);

        if (null != session) {
            if (!session.getId().equals(session.getDeviceId())) {
                repository.remove(session.getId().equals(idOrDeviceId) ? session.getDeviceId() : session.getId());
            }
            //????????????
            transportCounter
                    .computeIfAbsent(session.getTransport().getId(), transport -> new LongAdder())
                    .decrement();
            //??????????????????
            session.getOperator()
                    .offline()
                    .doFinally(s -> {
                        //??????
                        if (onDeviceUnRegister.hasDownstreams()) {
                            onDeviceUnRegister.onNext(session);
                        }
                    })
                    .subscribe();
            //???????????????
            Mono.justOrEmpty(children.remove(session.getDeviceId()))
                    .flatMapIterable(Map::values)
                    .flatMap(childrenDeviceSession -> childrenDeviceSession.getOperator()
                            .offline()
                            .doFinally(s -> {
                                if (onDeviceUnRegister.hasDownstreams()) {
                                    onDeviceUnRegister.onNext(childrenDeviceSession);
                                }
                                scheduleJobQueue.add(childrenDeviceSession::close);
                            })
                    )
                    .subscribe();

            //????????????????????????
            scheduleJobQueue.add(session::close);
        }
        return session;
    }

}
