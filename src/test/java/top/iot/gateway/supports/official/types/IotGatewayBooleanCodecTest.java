package top.iot.gateway.supports.official.types;

import top.iot.gateway.core.metadata.types.BooleanType;
import org.junit.Assert;
import org.junit.Test;

public class IotGatewayBooleanCodecTest {

    @Test
    public void testCodec(){
        IotGatewayBooleanCodec codec=new IotGatewayBooleanCodec();
        BooleanType type=new BooleanType();
        type.setTrueValue("1");
        type.setFalseValue("0");

        BooleanType newType=codec.decode(new BooleanType(), codec.encode(type));

        Assert.assertEquals(newType.getTrueValue(),"1");
        Assert.assertEquals(newType.getFalseValue(),"0");

    }
}