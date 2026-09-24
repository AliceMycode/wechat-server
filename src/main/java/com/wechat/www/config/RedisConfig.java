package com.wechat.www.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 配置类
 * 为什么需要：Spring Boot 自动配的 RedisTemplate 是 <Object,Object>、value 用 JDK 序列化，
 * 存进去的字符串会变成二进制乱码。我们自己定义 <String,Object> 的模板，
 * key 用字符串序列化、value 用 JSON 序列化，人可读、跨语言可读。
 * 自定义同名 bean 会自动覆盖 Spring Boot 的默认 bean，无需额外配置。
 */
@Configuration
public class RedisConfig {

  @Bean("redisTemplate")
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);                       // 连接工厂：Spring 按 yml 的 spring.data.redis 自动建好传进来
    template.setKeySerializer(RedisSerializer.string());          // key 用字符串序列化 → "wechat:checkcode:xxx"
    template.setValueSerializer(RedisSerializer.json());          // value 用 JSON 序列化 → 存字符串、后面存对象都兼容
    template.setHashKeySerializer(RedisSerializer.string());      // hash 结构的 key 同上
    template.setHashValueSerializer(RedisSerializer.json());      // hash 结构的 value 同上
    template.afterPropertiesSet();                                // Spring 要求：属性设置完成后调一次
    return template;
  }
}
