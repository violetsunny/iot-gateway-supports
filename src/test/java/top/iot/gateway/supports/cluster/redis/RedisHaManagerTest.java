package top.iot.gateway.supports.cluster.redis;

import top.iot.gateway.supports.cluster.RedisHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

public class RedisHaManagerTest {

    @Autowired
    private ReactiveRedisTemplate<Object, Object> operations= RedisHelper.getRedisTemplate();

    @Test
    public void test(){
        //RedisHaManager haManager=new RedisHaManager("test", ServerNode.builder().id("test").build(),)
    }
}