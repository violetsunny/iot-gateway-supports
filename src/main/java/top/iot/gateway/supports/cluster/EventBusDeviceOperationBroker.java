package top.iot.gateway.supports.cluster;

import com.google.common.cache.CacheBuilder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import top.iot.gateway.core.codec.Codec;
import top.iot.gateway.core.codec.Codecs;
import top.iot.gateway.core.device.DeviceStateInfo;
import top.iot.gateway.core.event.EventBus;
import top.iot.gateway.core.event.Subscription;
import top.iot.gateway.core.message.*;
import top.iot.gateway.core.trace.TraceHolder;
import top.iot.gateway.supports.cluster.redis.DeviceCheckRequest;
import top.iot.gateway.supports.cluster.redis.DeviceCheckResponse;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.google.common.cache.RemovalCause.EXPIRED;

@Deprecated
@Slf4j
public class EventBusDeviceOperationBroker extends AbstractDeviceOperationBroker implements Disposable {

    private static final Codec<Message> messageCodec = Codecs.lookup(Message.class);

    private final String serverId;

    private final EventBus eventBus;

    private final Disposable.Composite disposable = Disposables.composite();

    private final Map<String, Sinks.One<DeviceCheckResponse>> checkRequests = new ConcurrentHashMap<>();

    private Function<Publisher<String>, Flux<DeviceStateInfo>> localStateChecker;

    private final Map<String, RepayableDeviceMessage<?>> awaits = CacheBuilder
            .newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .<String, RepayableDeviceMessage<?>>removalListener(notify -> {
                if (notify.getCause() == EXPIRED) {
                    try {
                        EventBusDeviceOperationBroker.log.debug("discard await reply message[{}] message,{}", notify.getKey(), notify.getValue());
                    } catch (Throwable ignore) {
                    }
                }
            })
            .build()
            .asMap();

    public EventBusDeviceOperationBroker(String serverId, EventBus eventBus) {
        this.serverId = serverId;
        this.eventBus = eventBus;
    }

    @Override
    public void dispose() {
        disposable.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposable.isDisposed();
    }

    private void doSubscribeReply() {
        Subscription subscription = Subscription
                .of("device-message-broker",
                    new String[]{"/_sys/msg-broker-reply/" + serverId},
                    Subscription.Feature.broker);

        disposable.add(
                eventBus
                        .subscribe(subscription, messageCodec)
                        .filter(DeviceMessageReply.class::isInstance)
                        .cast(DeviceMessageReply.class)
                        .subscribe(this::handleReply)
        );
    }

    public void start() {
        {
            doSubscribeReply();
        }

        {
            Subscription subscription = Subscription
                    .of("device-state-checker",
                        new String[]{"/_sys/device-state-check-res/" + serverId},
                        Subscription.Feature.broker);

            disposable.add(eventBus
                                   .subscribe(subscription, DeviceCheckResponse.class)
                                   .subscribe(response -> Optional
                                           .ofNullable(checkRequests.remove(response.getRequestId()))
                                           .ifPresent(processor -> {
                                               processor.tryEmitValue(response);
                                           }))
            );
        }

    }

    @Override
    public Flux<DeviceStateInfo> getDeviceState(String deviceGatewayServerId,
                                                Collection<String> deviceIdList) {
        return Flux.defer(() -> {
            //本地检查
            if (serverId.equals(deviceGatewayServerId) && localStateChecker != null) {
                return localStateChecker.apply(Flux.fromIterable(deviceIdList));
            }
            long startWith = System.currentTimeMillis();
            String uid = UUID.randomUUID().toString();

            DeviceCheckRequest request = new DeviceCheckRequest(serverId, uid, new ArrayList<>(deviceIdList));
            Sinks.One<DeviceCheckResponse> processor = Sinks.one();

            checkRequests.put(uid, processor);

            return eventBus
                    .publish("/_sys/device-state-check/".concat(deviceGatewayServerId), request)
                    .flatMapMany(i -> {
                        if (i == 0) {
                            log.warn("gateway server [{}] not found", deviceGatewayServerId);
                            return Mono.empty();
                        }
                        return processor.asMono().flatMapIterable(DeviceCheckResponse::getStateInfoList);
                    })
                    .timeout(Duration.ofSeconds(5), Flux.empty())
                    .doFinally((s) -> {
                        log.trace("check device state complete take {}ms", System.currentTimeMillis() - startWith);
                        checkRequests.remove(uid);
                    });
        });
    }

    @Override
    public Disposable handleGetDeviceState(String serverId, Function<Publisher<String>, Flux<DeviceStateInfo>> stateMapper) {
        Subscription subscription = Subscription
                .of("device-state-checker",
                    new String[]{"/_sys/device-state-check/" + serverId},
                    Subscription.Feature.broker);
        this.localStateChecker = stateMapper;
        return eventBus
                .subscribe(subscription, DeviceCheckRequest.class)
                .subscribe(request -> stateMapper
                        .apply(Flux.fromIterable(request.getDeviceId()))
                        .collectList()
                        .map(resp -> new DeviceCheckResponse(resp, request.getRequestId()))
                        .flatMap(res -> eventBus.publish("/_sys/device-state-check-res/" + request.getFrom(), res))
                        .subscribe());
    }


    @Override
    protected Mono<Void> doReply(DeviceMessageReply reply) {
        String serverId = Optional
                .ofNullable(awaits.remove(getAwaitReplyKey(reply)))
                .flatMap(req -> req.getHeader(Headers.sendFrom))
                .orElse("*");
        return eventBus
                .publish("/_sys/msg-broker-reply/" + serverId, messageCodec, reply)
                .doOnNext(i -> {
                    if (i <= 0 && !"*".equals(serverId)) {
                        log.warn("no handler [{}] for reply message : {}", serverId, reply);
                    }
                })
                .then();
    }

    @Override
    public Mono<Integer> send(String deviceGatewayServerId, Publisher<? extends Message> message) {
        return eventBus
                .publish("/_sys/msg-broker/" + deviceGatewayServerId, messageCodec,
                         Flux.from(message)
                             .doOnNext(msg -> msg.addHeader(Headers.sendFrom, serverId))
                )
                .map(Long::intValue);
    }

    @Override
    public Mono<Integer> send(Publisher<? extends BroadcastMessage> message) {
        return eventBus
                .publish("/_sys/msg-broker-broadcast", messageCodec, message)
                .map(Long::intValue);
    }

    @Override
    public Flux<Message> handleSendToDeviceMessage(String serverId) {
        Subscription subscription = Subscription
                .of("device-message-broker",
                    new String[]{"/_sys/msg-broker/" + serverId},
                    Subscription.Feature.local,
                    Subscription.Feature.broker);

        return eventBus
                .subscribe(subscription)
                .map(payload -> {
                    try {
                        //copy trace上下文
                        Message message = payload.decode(messageCodec, false);
                        return TraceHolder.copyContext(payload.getHeaders(), message, Message::addHeader);
                    } finally {
                        ReferenceCountUtil.safeRelease(payload);
                    }
                })
                .doOnNext(message -> {
                    if (message instanceof RepayableDeviceMessage && !message
                            .getHeader(Headers.sendAndForget)
                            .orElse(false)) {
                        boolean isSameServer = message
                                .getHeader(Headers.sendFrom)
                                .map(sendFrom -> sendFrom.equals(serverId))
                                .orElse(false);

                        if (!isSameServer) {
                            awaits.put(getAwaitReplyKey((RepayableDeviceMessage<?>) message), (RepayableDeviceMessage<?>) message);
                        }
                    }
                });
    }

}
