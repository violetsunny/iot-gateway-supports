package top.iot.gateway.supports.test;

import top.iot.gateway.core.Value;
import top.iot.gateway.core.device.DeviceInfo;
import top.iot.gateway.core.device.ProductInfo;
import org.junit.Test;
import reactor.test.StepVerifier;

public class InMemoryDeviceRegistryTest {

    @Test
    public void test() {

        InMemoryDeviceRegistry registry = InMemoryDeviceRegistry.create();

        registry.register(ProductInfo
                                  .builder()
                                  .id("test")
                                  .protocol("test")
                                  .build())

                .then(
                        registry.register(DeviceInfo.builder()
                                                    .productId("test")
                                                    .id("test")
                                                    .build())
                )
                .then(registry.getDevice("test"))
                .flatMap(device -> device.setConfig("test", "1234").thenReturn(device))
                .flatMap(device -> device.getSelfConfig("test").map(Value::asString))
                .as(StepVerifier::create)
                .expectNext("1234")
                .verifyComplete();


    }
}