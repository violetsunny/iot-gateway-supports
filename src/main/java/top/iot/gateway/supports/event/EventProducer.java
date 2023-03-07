package top.iot.gateway.supports.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.event.*;

/**
 * 事件生产者
 */
public interface EventProducer extends EventConnection {

    /**
     * 发送订阅请求
     *
     * @param subscription 订阅请求
     */
    Mono<Void> subscribe(Subscription subscription);

    /**
     * 发送取消订阅请求
     *
     * @param subscription 订阅请求
     */
    Mono<Void> unsubscribe(Subscription subscription);

    /**
     * 从生产者订阅消息
     *
     * @return 消息流
     */
    Flux<TopicPayload> subscribe();

}
