package top.iot.gateway.supports;

import top.iot.gateway.supports.protocol.ServiceLoaderProtocolSupports;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ServiceLoaderProtocolSupportsTest {
    private ServiceLoaderProtocolSupports supports = new ServiceLoaderProtocolSupports();

    @Before
    public void init() {

        supports.init();
    }

    @Test
    public void test() {
        Assert.assertTrue(supports.isSupport("iot-gateway.v1.0"));
    }

}