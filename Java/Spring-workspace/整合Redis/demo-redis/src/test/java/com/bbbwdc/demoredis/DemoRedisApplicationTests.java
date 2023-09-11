package com.bbbwdc.demoredis;

import com.bbbwdc.demoredis.DO.UserCacheObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DemoRedisApplicationTests {

	@Autowired
	RedisTemplate redisTemplate;
	@Test
	public void contextLoads() {
		redisTemplate.opsForValue().set("testKey", "value");
	}

	@Test
	public void testStringSetKeyUserCache() {
		UserCacheObject object = new UserCacheObject();
		object.setName("测试");
		object.setId(1);
		object.setGender(1);
		String key = String.format("user:%d", object.getId());
		redisTemplate.opsForValue().set(key, object);
	}

	@Test
	public void testStringGetKeyUserCache() {
		String key = String.format("user:%d", 1);
		Object value = redisTemplate.opsForValue().get(key);
		System.out.println(value);
	}
}
