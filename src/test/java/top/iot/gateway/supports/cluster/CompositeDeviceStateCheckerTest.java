package top.iot.gateway.supports.cluster;

import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.iot.gateway.core.device.DeviceOperator;
import top.iot.gateway.core.device.DeviceState;
import top.iot.gateway.core.device.DeviceStateChecker;

import javax.validation.constraints.NotNull;

public class CompositeDeviceStateCheckerTest {

    @Test
    public void test(){
        CompositeDeviceStateChecker stateChecker=new CompositeDeviceStateChecker();

        stateChecker.addDeviceStateChecker(new DeviceStateChecker() {
            @Override
            public @NotNull Mono<Byte> checkState(@NotNull DeviceOperator device) {
                return Mono.empty();
            }

            @Override
            public long order() {
                return 0;
            }
        });

        stateChecker.addDeviceStateChecker(new DeviceStateChecker() {
            @Override
            public @NotNull Mono<Byte> checkState(@NotNull DeviceOperator device) {
                return Mono.just(DeviceState.online);
            }

            @Override
            public long order() {
                return 1;
            }
        });

        stateChecker.checkState(null)
                    .as(StepVerifier::create)
                    .expectNext(DeviceState.online)
                    .verifyComplete();

    }
}