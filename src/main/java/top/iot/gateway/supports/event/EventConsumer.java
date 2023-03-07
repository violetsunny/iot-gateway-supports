package top.iot.gateway.supports.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import top.iot.gateway.core.event.*;

/**
 * 事件消费者
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface EventConsumer extends EventConnection {

    Flux<Subscription> handleSubscribe();

    Flux<Subscription> handleUnSubscribe();

    FluxSink<TopicPayload> sink();

}
