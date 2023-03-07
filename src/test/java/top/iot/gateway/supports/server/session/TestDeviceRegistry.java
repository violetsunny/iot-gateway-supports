package top.iot.gateway.supports.server.session;

import top.iot.gateway.core.ProtocolSupports;
import top.iot.gateway.core.config.ConfigStorageManager;
import top.iot.gateway.core.defaults.DefaultDeviceOperator;
import top.iot.gateway.core.defaults.DefaultDeviceProductOperator;
import top.iot.gateway.core.device.*;
import top.iot.gateway.core.message.interceptor.DeviceMessageSenderInterceptor;
import top.iot.gateway.supports.config.InMemoryConfigStorageManager;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestDeviceRegistry implements DeviceRegistry {

    private DeviceMessageSenderInterceptor interceptor = new CompositeDeviceMessageSenderInterceptor();

    private ConfigStorageManager manager = new InMemoryConfigStorageManager();

    private Map<String, DeviceOperator> operatorMap = new ConcurrentHashMap<>();

    private Map<String, DeviceProductOperator> productOperatorMap = new ConcurrentHashMap<>();

    private ProtocolSupports supports;

    private DeviceOperationBroker handler;

    public TestDeviceRegistry(ProtocolSupports supports, DeviceOperationBroker handler) {
        this.supports = supports;
        this.handler = handler;
    }

    @Override
    public Mono<DeviceOperator> getDevice(String deviceId) {
        return Mono.fromSupplier(() -> operatorMap.get(deviceId));
    }

    @Override
    public Mono<DeviceProductOperator> getProduct(String productId) {
        return Mono.fromSupplier(() -> productOperatorMap.get(productId));
    }

    @Override
    public Mono<DeviceOperator> register(DeviceInfo deviceInfo) {
        DefaultDeviceOperator operator = new DefaultDeviceOperator(
                deviceInfo.getId(),
                supports, manager, handler, this
        );
        operatorMap.put(operator.getDeviceId(), operator);
        return operator.setConfigs(
                DeviceConfigKey.productId.value(deviceInfo.getProductId()),
                DeviceConfigKey.protocol.value(deviceInfo.getProtocol()))
                .thenReturn(operator);
    }

    @Override
    public Mono<DeviceProductOperator> register(ProductInfo productInfo) {
        DefaultDeviceProductOperator operator = new DefaultDeviceProductOperator(productInfo.getId(), supports, manager);
        productOperatorMap.put(operator.getId(), operator);
        return operator.setConfigs(
                DeviceConfigKey.productId.value(productInfo.getMetadata()),
                DeviceConfigKey.protocol.value(productInfo.getProtocol()))
                .thenReturn(operator);
    }

    @Override
    public Mono<Void> unregisterDevice(String deviceId) {
        return Mono.justOrEmpty(deviceId)
                .map(operatorMap::remove)
                .then();
    }

    @Override
    public Mono<Void> unregisterProduct(String productId) {
        return Mono.justOrEmpty(productId)
                .map(productOperatorMap::remove)
                .then();
    }
}
