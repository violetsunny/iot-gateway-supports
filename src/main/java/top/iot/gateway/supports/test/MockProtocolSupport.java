package top.iot.gateway.supports.test;

import top.iot.gateway.supports.official.IotGatewayDeviceMetadataCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.ProtocolSupport;
import top.iot.gateway.core.ProtocolSupports;
import top.iot.gateway.core.device.AuthenticationRequest;
import top.iot.gateway.core.device.AuthenticationResponse;
import top.iot.gateway.core.device.DeviceOperator;
import top.iot.gateway.core.device.DeviceRegistry;
import top.iot.gateway.core.message.codec.DeviceMessageCodec;
import top.iot.gateway.core.message.codec.Transport;
import top.iot.gateway.core.metadata.DeviceMetadataCodec;

import javax.annotation.Nonnull;

public class MockProtocolSupport implements ProtocolSupport, ProtocolSupports {
    @Nonnull
    @Override
    public String getId() {
        return "test";
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "test";
    }

    @Override
    public Flux<Transport> getSupportedTransport() {
        return Flux.empty();
    }

    @Nonnull
    @Override
    public Mono<DeviceMessageCodec> getMessageCodec(Transport transport) {
        return Mono.empty();
    }


    @Nonnull
    @Override
    public DeviceMetadataCodec getMetadataCodec() {
        return IotGatewayDeviceMetadataCodec.getInstance();
    }

    @Nonnull
    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator deviceOperation) {
        return Mono.just(AuthenticationResponse.success(deviceOperation.getDeviceId()));
    }

    @Nonnull
    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        return Mono.just(AuthenticationResponse.success("test"));
    }

    @Override
    public boolean isSupport(String protocol) {
        return true;
    }

    @Override
    public Mono<ProtocolSupport> getProtocol(String protocol) {
        return Mono.just(this);
    }

    @Override
    public Flux<ProtocolSupport> getProtocols() {
        return Flux.just(this);
    }
}
