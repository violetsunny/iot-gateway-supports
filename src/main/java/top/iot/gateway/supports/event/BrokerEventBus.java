package top.iot.gateway.supports.event;

import io.lettuce.core.event.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThreadLocalRandom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hswebframework.web.dict.EnumDict;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.iot.gateway.core.Payload;
import top.iot.gateway.core.codec.Codecs;
import top.iot.gateway.core.codec.Decoder;
import top.iot.gateway.core.codec.Encoder;
import top.iot.gateway.core.event.*;
import top.iot.gateway.core.event.EventBus;
import top.iot.gateway.core.topic.Topic;
import top.iot.gateway.core.trace.TraceHolder;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 支持事件代理的事件总线,可通过代理来实现集群和分布式事件总线
 *
 * @author zhouhao
 * @see EventBroker
 * @since 1.1.1
 */
public class BrokerEventBus implements EventBus {

    private final Topic<SubscriptionInfo> root = Topic.createRoot();

    private final Map<String, EventBroker> brokers = new ConcurrentHashMap<>(32);

    private final Map<String, EventConnection> connections = new ConcurrentHashMap<>(512);

    @Setter
    private Scheduler publishScheduler = Schedulers.immediate();

    @Setter
    private Logger log = LoggerFactory.getLogger(BrokerEventBus.class);

    public BrokerEventBus() {
    }

    @Override
    public <T> Flux<T> subscribe(@NotNull Subscription subscription,
                                 @NotNull Decoder<T> decoder) {
        return this
                .subscribe(subscription)
                .flatMap(payload -> {
                    try {
                        //收到消息后解码
                        return Mono.justOrEmpty(payload.decode(decoder, false));
                    } catch (Throwable e) {
                        //忽略解码错误,如果返回错误,可能会导致整个流中断
                        log.error("decode message [{}] error", payload.getTopic(), e);
                    } finally {
                        ReferenceCountUtil.safeRelease(payload);
                    }
                    return Mono.empty();
                })
                .publishOn(publishScheduler);
    }

    @Override
    public Flux<TopicPayload> subscribe(Subscription subscription) {
        return Flux
                .<TopicPayload>create(sink -> {
                    Disposable.Composite disposable = Disposables.composite();
                    String subscriberId = subscription.getSubscriber();
                    for (String topic : subscription.getTopics()) {
                        //追加订阅信息到订阅表中
                        Topic<SubscriptionInfo> topicInfo = root.append(topic);
                        SubscriptionInfo subInfo = SubscriptionInfo.of(
                                subscriberId,
                                EnumDict.toMask(subscription.getFeatures()),
                                sink,
                                false
                        );
                        //添加订阅信息
                        topicInfo.subscribe(subInfo);
                        //Flux结束时(dispose,error),取消订阅
                        disposable.add(() -> {
                            topicInfo.unsubscribe(subInfo);
                            subInfo.dispose();
                        });
                    }
                    sink.onDispose(disposable);
                    //从其他代理中订阅消息,比如集群.
                    if (subscription.hasFeature(Subscription.Feature.broker)) {
                        doSubscribeBroker(subscription)
                                .doOnSuccess(nil -> {
                                    if (subscription.getDoOnSubscribe() != null) {
                                        subscription.getDoOnSubscribe().run();
                                    }
                                })
                                .subscribe();

                        //Flux dislose时从集群取消订阅
                        disposable.add(() -> doUnsubscribeBroker(subscription).subscribe());
                    } else {
                        if (subscription.getDoOnSubscribe() != null) {
                            subscription.getDoOnSubscribe().run();
                        }
                    }
                    log.debug("local subscriber [{}],features:{},topics: {}",
                              subscriberId,
                              subscription.getFeatures(),
                              subscription.getTopics());
                })
                //TopicPayload被丢弃时自定释放
                .doOnDiscard(TopicPayload.class, ReferenceCountUtil::safeRelease);
    }

    public void addBroker(EventBroker broker) {
        brokers.put(broker.getId(), broker);
        startBroker(broker);
    }

    public void removeBroker(EventBroker broker) {
        brokers.remove(broker.getId());
    }

    public void removeBroker(String broker) {
        brokers.remove(broker);
    }

    public List<EventBroker> getBrokers() {
        return new ArrayList<>(brokers.values());
    }

    private Mono<Void> doSubscribeBroker(Subscription subscription) {
        return Flux.fromIterable(connections.values())
                   .filter(conn -> conn.isProducer() && conn.isAlive())
                   .cast(EventProducer.class)
                   .flatMap(conn -> conn.subscribe(subscription))
                   .then();

    }

    private Mono<Void> doUnsubscribeBroker(Subscription subscription) {
        return Flux.fromIterable(connections.values())
                   .filter(conn -> conn.isProducer() && conn.isAlive())
                   .cast(EventProducer.class)
                   .flatMap(conn -> conn.unsubscribe(subscription))
                   .then();
    }

    private void startBroker(EventBroker broker) {
        broker.accept()
              .subscribe(connection -> {
                  String connectionId = broker.getId().concat(":").concat(connection.getId());
                  EventConnection old = connections.put(connectionId, connection);
                  if (old == connection) {
                      return;
                  }
                  if (old != null) {
                      old.dispose();
                  }
                  connection.doOnDispose(() -> connections.remove(connectionId));

                  //从生产者订阅消息并推送到本地
                  connection
                          .asProducer()
                          .flatMap(eventProducer -> root
                                  .getAllSubscriber()
                                  .doOnNext(sub -> {
                                      for (SubscriptionInfo subscriber : sub.getSubscribers()) {
                                          //只处理本地订阅者并且需要订阅其他代理消息的订阅者
                                          if (subscriber.isLocal()) {
                                              if (subscriber.hasFeature(Subscription.Feature.broker)) {
                                                  eventProducer
                                                          .subscribe(subscriber.toSubscription(sub.getTopic()))
                                                          .subscribe();
                                              }
                                          }
                                      }
                                  })
                                  .then(Mono.just(eventProducer)))
                          //接收来自代理的消息
                          .flatMapMany(EventProducer::subscribe)
                          .flatMap(payload -> this
                                  .doPublishFromBroker(payload, sub -> {
                                      //只推送给订阅了代理的本地订阅者
                                      if (sub.isLocal()) {
                                          return sub.hasFeature(Subscription.Feature.broker);
                                      }
                                      //跨broker间转发,比如A推送给B,B推送给C.
                                      if (sub.isBroker()) {
                                          //消息来自同一个broker
                                          if (sub.getEventBroker() == broker) {
                                              if (sub.getEventConnection() == connection) {
                                                  return sub.hasConnectionFeature(EventConnection.Feature.consumeSameConnection);
                                              }
                                              return sub.hasConnectionFeature(EventConnection.Feature.consumeSameBroker);
                                          }
                                          return sub.hasConnectionFeature(EventConnection.Feature.consumeAnotherBroker);
                                      }
                                      return false;

                                  }), Integer.MAX_VALUE)
                          .onErrorContinue((err, obj) -> {
                              log.error(err.getMessage(), err);
                          })
                          .subscribe();

                  //从消费者订阅获取订阅消息请求
                  connection
                          .asConsumer()
                          .subscribe(subscriber -> {
                              //接收订阅请求
                              subscriber
                                      .handleSubscribe()
                                      .doOnNext(subscription ->
                                                        handleBrokerSubscription(
                                                                subscription,
                                                                SubscriptionInfo.of(
                                                                                        subscription.getSubscriber(),
                                                                                        EnumDict.toMask(subscription.getFeatures()),
                                                                                        subscriber.sink(),
                                                                                        true)
                                                                                .connection(broker, connection),
                                                                connection
                                                        ))
                                      .onErrorContinue((err, obj) -> {
                                          log.error(err.getMessage(), err);
                                      })
                                      .subscribe();

                              //接收取消订阅请求
                              subscriber
                                      .handleUnSubscribe()
                                      .doOnNext(subscription ->
                                                        handleBrokerUnsubscription(
                                                                subscription,
                                                                SubscriptionInfo.of(subscription.getSubscriber()),
                                                                connection
                                                        ))
                                      .onErrorContinue((err, obj) -> {
                                          log.error(err.getMessage(), err);
                                      })
                                      .subscribe();
                          });
              });
    }

    private void handleBrokerUnsubscription(Subscription subscription, SubscriptionInfo info, EventConnection connection) {
        if (log.isDebugEnabled()) {
            log.debug("broker [{}] unsubscribe : {}", info, subscription.getTopics());
        }
        for (String topic : subscription.getTopics()) {
            AtomicBoolean unsub = new AtomicBoolean(false);
            root.append(topic)
                .unsubscribe(sub -> sub.getEventConnection() == connection
                        && sub.getSubscriber().equals(info.getSubscriber())
                        && unsub.compareAndSet(false, true)
                );
        }
    }

    private void subAnotherBroker(Subscription subscription, SubscriptionInfo info, EventConnection connection) {
        //从其他broker订阅时,去掉broker标识
        //todo 还有更好到处理方式？
        Subscription sub = subscription.hasFeature(Subscription.Feature.shared)
                ? subscription.copy(Subscription.Feature.shared, Subscription.Feature.local)
                : subscription.copy(Subscription.Feature.local);

        Flux.fromIterable(connections.values())
            .filter(conn -> {
                if (conn == connection) {
                    return info.hasConnectionFeature(EventConnection.Feature.consumeSameConnection);
                }
                if (conn.getBroker() == connection.getBroker()) {
                    return info.hasConnectionFeature(EventConnection.Feature.consumeSameBroker);
                }
                return true;
            })
            .flatMap(EventConnection::asProducer)
            .flatMap(eventProducer -> eventProducer.subscribe(sub))
            .subscribe();
    }

    //处理来自其他broker的订阅请求
    private void handleBrokerSubscription(Subscription subscription, SubscriptionInfo info, EventConnection connection) {
        if (log.isDebugEnabled()) {
            log.debug("broker [{}] subscribe : {}", info, subscription.getTopics());
        }
        for (String topic : subscription.getTopics()) {
            Topic<SubscriptionInfo> topic_ = root.append(topic);
            topic_.subscribe(info);
            info.onDispose(() -> topic_.unsubscribe(info));
        }
        if (subscription.hasFeature(Subscription.Feature.broker)
                && info.hasConnectionFeature(EventConnection.Feature.consumeAnotherBroker)) {
            subAnotherBroker(subscription, info, connection);
        }

    }

    private boolean doPublish(String topic, SubscriptionInfo info, TopicPayload payload) {
        try {
            //已经取消订阅则不推送
            if (info.sink.isCancelled()) {
                return false;
            }
            payload.retain();
            info.sink.next(payload);
            if (log.isDebugEnabled()) {
                log.debug("publish [{}] to [{}] complete", topic, info);
            }
            return true;
        } catch (Throwable error) {
            log.error("publish [{}] to [{}] event error", topic, info, error);
            ReferenceCountUtil.safeRelease(payload);
        }
        return false;
    }

    private long doPublish(String topic,
                           Predicate<SubscriptionInfo> predicate,
                           Consumer<SubscriptionInfo> subscriberConsumer) {
        //共享订阅,只有一个订阅者能收到
        Map<String, List<SubscriptionInfo>> sharedMap = new HashMap<>();
        //去重
        Set<Object> distinct = new HashSet<>(64);
        //从订阅表中查找topic
        root.findTopic(topic, subs -> {
            for (SubscriptionInfo sub : subs.getSubscribers()) {
                //broker已经失效则不推送
                if (sub.isBroker() && !sub.getEventConnection().isAlive()) {
                    sub.dispose();
                    continue;
                }
                if (!predicate.test(sub) || !distinct.add(sub.sink)) {
                    continue;
                }
                //共享订阅时,添加到缓存,最后再处理
                if (sub.hasFeature(Subscription.Feature.shared)) {
                    sharedMap
                            .computeIfAbsent(sub.subscriber, ignore -> new ArrayList<>(8))
                            .add(sub);
                    continue;
                }
                subscriberConsumer.accept(sub);
            }
        }, () -> {
            //处理共享订阅
            for (List<SubscriptionInfo> value : sharedMap.values()) {
                subscriberConsumer.accept(value.get(ThreadLocalRandom.current().nextInt(0, value.size())));
            }
        });
        return distinct.size();
    }

    private Mono<Long> doPublishFromBroker(TopicPayload payload, Predicate<SubscriptionInfo> predicate) {
        long total = this
                .doPublish(
                        payload.getTopic(),
                        predicate,
                        info -> {
                            try {
                                payload.retain();
                                info.sink.next(payload);
                                if (log.isDebugEnabled()) {
                                    log.debug("broker publish [{}] to [{}] complete", payload.getTopic(), info);
                                }
                            } catch (Throwable e) {
                                log.warn("broker publish [{}] to [{}] error", payload.getTopic(), info, e);
                            }
                        }
                );
        ReferenceCountUtil.safeRelease(payload);
        return Mono.just(total);
    }

    @Override
    @SuppressWarnings("all")
    public <T> Mono<Long> publish(String topic, Publisher<T> event) {
        return publish(topic, (Encoder<T>) msg -> Codecs.lookup((Class<T>) msg.getClass()).encode(msg), event);
    }


    @Override
    @SneakyThrows
    public <T> Mono<Long> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream) {
        return publish(topic, encoder, eventStream, publishScheduler);
    }

    @Override
    public <T> Mono<Long> publish(String topic, Encoder<T> encoder, T event) {
        return this.publish(topic, encoder, event, publishScheduler);
    }

    @Override
    public <T> Mono<Long> publish(String topic, Encoder<T> encoder, T payload, Scheduler scheduler) {
        return TraceHolder
                //写入跟踪信息到header中
                .writeContextTo(TopicPayload.of(topic, Payload.of(payload, encoder)), TopicPayload::addHeader)
                .map(pld -> {
                    long subs = this
                            .doPublish(pld.getTopic(),
                                       sub -> !sub.isLocal() || sub.hasFeature(Subscription.Feature.local),
                                       sub -> doPublish(pld.getTopic(), sub, pld)
                            );
                    if (log.isTraceEnabled()) {
                        log.trace("topic [{}] has {} subscriber", pld.getTopic(), subs);
                    }
                    ReferenceCountUtil.safeRelease(pld);
                    return subs;
                });
    }

    @Override
    public <T> Mono<Long> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream, Scheduler publisher) {
        Flux<TopicPayload> cache = Flux
                .from(eventStream)
                .flatMap(payload -> TraceHolder
                        //写入跟踪信息到header中
                        .writeContextTo(TopicPayload.of(topic, Payload.of(payload, encoder)), TopicPayload::addHeader))
                .cache();
        return Flux
                .<SubscriptionInfo>create(sink -> {
                    this.doPublish(topic,
                                   sub -> !sub.isLocal() || sub.hasFeature(Subscription.Feature.local),
                                   sink::next
                    );
                    sink.complete();
                })
                .flatMap(sub -> cache
                        .doOnNext(payload -> doPublish(topic, sub, payload))
                        .count())
                .count()
                .flatMap((s) -> {
                    // if (s > 0) {
                    //释放Payload
                    return cache
                            .map(payload -> {
                                ReferenceCountUtil.safeRelease(payload);
                                return true;
                            })
                            .then(Mono.just(s));
                    //  }
                    //  return Mono.just(s);
                });

    }

    @AllArgsConstructor(staticName = "of")
    @Getter
    @Setter
    private static class SubscriptionInfo implements Disposable {
        String subscriber;
        long features;
        FluxSink<TopicPayload> sink;
        @Getter
        boolean broker;

        Composite disposable;

        //broker only
        EventBroker eventBroker;

        EventConnection eventConnection;

        long connectionFeatures;

        @Override
        public String toString() {
            return isLocal() ? subscriber + "@local" : subscriber + "@" + eventBroker.getId() + ":" + eventConnection.getId();
        }

        public Subscription toSubscription(String topic) {
            return Subscription.of(subscriber, new String[]{topic}, EnumDict
                    .getByMask(Subscription.Feature.class, features)
                    .toArray(new Subscription.Feature[0]));
        }

        public SubscriptionInfo connection(EventBroker broker, EventConnection connection) {
            this.eventConnection = connection;
            this.eventBroker = broker;
            this.connectionFeatures = EnumDict.toMask(connection.features());
            return this;
        }

        public static SubscriptionInfo of(String subscriber) {
            return of(subscriber, 0, null, false);
        }

        public static SubscriptionInfo of(Subscription subscription,
                                          FluxSink<TopicPayload> sink,
                                          boolean remote) {
            return of(subscription.getSubscriber(),
                      EnumDict.toMask(subscription.getFeatures()),
                      sink,
                      remote);
        }

        public static SubscriptionInfo of(String subscriber,
                                          long features,
                                          FluxSink<TopicPayload> sink,
                                          boolean remote) {
            return new SubscriptionInfo(subscriber, features, sink, remote);
        }

        public SubscriptionInfo(String subscriber,
                                long features,
                                FluxSink<TopicPayload> sink,
                                boolean broker) {
            this.subscriber = subscriber;
            this.features = features;
            this.sink = sink;
            this.broker = broker;
        }

        synchronized void onDispose(Disposable disposable) {
            if (this.disposable == null) {
                this.disposable = Disposables.composite(disposable);
            } else {
                this.disposable.add(disposable);
            }
        }

        public void dispose() {
            if (disposable != null) {
                disposable.dispose();
            }
        }

        boolean isLocal() {
            return !broker;
        }

        boolean hasFeature(Subscription.Feature feature) {
            return feature.in(this.features);
        }

        boolean hasConnectionFeature(EventConnection.Feature feature) {
            return feature.in(this.connectionFeatures);
        }
    }
}
