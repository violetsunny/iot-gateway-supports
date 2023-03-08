package top.iot.gateway.supports.protocol.management.jar;

import top.iot.gateway.core.ProtocolSupport;
import top.iot.gateway.core.Value;
import top.iot.gateway.core.config.ConfigKey;
import top.iot.gateway.core.spi.ServiceContext;
import top.iot.gateway.supports.protocol.management.ProtocolSupportDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class JarProtocolSupportLoaderTest {
    ServiceContext context = new ServiceContext() {
        @Override
        public Optional<Value> getConfig(ConfigKey<String> key) {
            return Optional.empty();
        }

        @Override
        public Optional<Value> getConfig(String key) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> getService(Class<T> service) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> getService(String service) {
            return Optional.empty();
        }

        @Override
        public <T> List<T> getServices(Class<T> service) {
            return Collections.emptyList();
        }

        @Override
        public <T> List<T> getServices(String service) {
            return Collections.emptyList();
        }
    };

    //@Test
    public void test() {
        JarProtocolSupportLoader loader = new JarProtocolSupportLoader();
        loader.setServiceContext(context);
        String location = this.getClass().getResource("/protocol-test-1.0-SNAPSHOT.jar").getPath();

        Map<String, Object> config = new HashMap<>();
        config.put("location", location);
      //  config.put("provider", "org.iot-gateway.demo.TestProtocolSupportProvider");

        ProtocolSupport support = loader.load(ProtocolSupportDefinition.builder()
                .id("test")
                .configuration(config)
                .build())
                .block();



        Assert.assertNotNull(support);
        Assert.assertEquals(support.getId(), "test");

    }
}