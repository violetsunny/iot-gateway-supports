package top.iot.gateway.supports.device.session;


import io.scalecube.cluster.ClusterConfig;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import lombok.SneakyThrows;
import top.iot.gateway.core.device.DeviceInfo;
import top.iot.gateway.core.device.DeviceOperator;
import top.iot.gateway.core.device.DeviceRegistry;
import top.iot.gateway.core.device.ProductInfo;
import top.iot.gateway.core.message.codec.DefaultTransport;
import top.iot.gateway.core.server.session.LostDeviceSession;
import top.iot.gateway.core.utils.Reactors;
import top.iot.gateway.supports.scalecube.ExtendedClusterImpl;
import top.iot.gateway.supports.scalecube.rpc.ScalecubeRpcManager;
import top.iot.gateway.supports.test.InMemoryDeviceRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterDeviceSessionManagerTest {
    ClusterDeviceSessionManager manager1;
    ClusterDeviceSessionManager manager2;
    DeviceRegistry registry;

    DeviceOperator device;

    @Before
    @SneakyThrows
    public void init() {
        ExtendedClusterImpl cluster1 = new ExtendedClusterImpl(
                ClusterConfig.defaultConfig()
                             .transport(conf -> conf.transportFactory(new TcpTransportFactory()))
        );
        cluster1.startAwait();


        ExtendedClusterImpl cluster2 = new ExtendedClusterImpl(
                ClusterConfig.defaultConfig()
                             .transport(conf -> conf.transportFactory(new TcpTransportFactory()))
                             .membership(ship -> ship.seedMembers(cluster1.address()))
        );
        cluster2.startAwait();

        ScalecubeRpcManager rpcManager = new ScalecubeRpcManager(cluster1, RSocketServiceTransport::new);
        ScalecubeRpcManager rpcManager2 = new ScalecubeRpcManager(cluster2, RSocketServiceTransport::new);
        rpcManager.startAwait();
        rpcManager2.startAwait();

        manager1 = new ClusterDeviceSessionManager(rpcManager);
        manager2 = new ClusterDeviceSessionManager(rpcManager2);

        manager1.init();
        manager2.init();
        Thread.sleep(1000);

        registry = InMemoryDeviceRegistry.create();

        registry.register(ProductInfo.builder()
                                     .id("test")
                                     .protocol("test")
                                     .metadata("{}")
                                     .build())
                .block();

        device = registry.register(DeviceInfo.builder()
                                             .id("test")
                                             .productId("test")
                                             .build())
                         .block();
    }

    //@Test
    public void testRegisterInMulti() {
        LostDeviceSession session = new LostDeviceSession("test", device, DefaultTransport.MQTT) {
            @Override
            public boolean isAlive() {
                return true;
            }
            @Override
            public Mono<Boolean> isAliveAsync() {
                return Reactors.ALWAYS_TRUE;
            }
        };
        AtomicInteger eventCount = new AtomicInteger();
        Disposables.composite(
                manager1.listenEvent(event -> {
                    if (event.isClusterExists()) {
                        return Mono.empty();
                    }
                    eventCount.incrementAndGet();
                    return Mono.empty();
                }),
                manager2.listenEvent(event -> {
                    if (event.isClusterExists()) {
                        return Mono.empty();
                    }
                    eventCount.incrementAndGet();
                    return Mono.empty();
                })
        );

        manager1.compute(session.getDeviceId(), mono -> Mono.just(session))
                .block();
        manager1.compute(session.getDeviceId(), mono -> Mono.just(session))
                .block();
        manager2.compute(session.getDeviceId(), Mono.just(session), null)
                .block();

        Assert.assertEquals(1, eventCount.get());
        eventCount.set(0);
        manager1.remove(device.getDeviceId(), false)
                .as(StepVerifier::create)
                .expectNext(2L)
                .verifyComplete();

        Assert.assertEquals(1, eventCount.get());
        Flux.merge(manager1.getSession(device.getDeviceId()),
                   manager2.getSession(device.getDeviceId()))
            .as(StepVerifier::create)
            .expectComplete()
            .verify();


    }

    //@Test
    public void testRegister() {
        LostDeviceSession session = new LostDeviceSession("test", device, DefaultTransport.MQTT) {
            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public Mono<Boolean> isAliveAsync() {
                return Reactors.ALWAYS_TRUE;
            }
        };
        manager1.compute(session.getDeviceId(), mono -> Mono.just(session))
                .block();

        manager1.isAlive(session.getDeviceId())
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        Duration time = Flux.range(0, 20000)
                            .flatMap(i -> manager2
                                    .isAlive(session.getDeviceId()))
                            .as(StepVerifier::create)
                            .expectNextCount(20000)
                            .verifyComplete();
        System.out.println(time);

        manager2.getSessionInfo()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }
}