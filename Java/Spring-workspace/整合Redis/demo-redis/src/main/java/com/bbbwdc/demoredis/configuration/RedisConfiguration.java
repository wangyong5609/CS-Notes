package com.bbbwdc.demoredis.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfiguration {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 创建 RedisTemplate 对象
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 设置 RedisConnection工厂
        template.setConnectionFactory(factory);
        // 设置key序列化方式，String
        template.setKeySerializer(RedisSerializer.string());
        // 设置value序列化方式，Json（库是Jackson）
        template.setValueSerializer(RedisSerializer.json());
        return template;
    }
}
