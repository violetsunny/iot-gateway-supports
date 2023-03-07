package top.iot.gateway.supports.cluster.event;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.cluster.ClusterManager;
import top.iot.gateway.core.codec.defaults.TopicPayloadCodec;
import top.iot.gateway.core.event.TopicPayload;

@Slf4j
@Deprecated
public class RedisClusterEventBroker extends AbstractClusterEventBroker {

    public RedisClusterEventBroker(ClusterManager clusterManager, ReactiveRedisConnectionFactory factory) {
        super(clusterManager, factory);
    }

    @Override
    protected Flux<TopicPayload> listen(String localId, String brokerId) {

//        return redis
//                .listenToChannel("/broker/bus/" + brokerId + "/" + localId)
//                .map(msg -> topicPayloadCodec.decode(Payload.of(Unpooled.wrappedBuffer(msg.getMessage()))));
//
        return clusterManager
                .<byte[]>getQueue("/broker/bus/" + brokerId + "/" + localId)
                .subscribe()
                .map(msg -> TopicPayloadCodec.doDecode(Unpooled.wrappedBuffer(msg)));
    }

    @Override
    protected Mono<Void> dispatch(String localId, String brokerId, TopicPayload payload) {
        try {
            ByteBuf byteBuf = TopicPayloadCodec.doEncode(payload);
            byte[] body = ByteBufUtil.getBytes(byteBuf);
            ReferenceCountUtil.safeRelease(payload);
            ReferenceCountUtil.safeRelease(byteBuf);

//        return redis
//                .convertAndSend("/broker/bus/" + localId + "/" + brokerId, body)
//                .then();
            return clusterManager
                    .getQueue("/broker/bus/" + localId + "/" + brokerId)
                    .add(Mono.just(body))
                    .then();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
        return Mono.empty();
    }
}
