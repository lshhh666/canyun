package com.sky.text;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.*;


@SpringBootTest
public class SpringDataRedisText {
    @Autowired
    private RedisTemplate redisTemplate;
    @Test
    public void  textRedisTemplate(){
        System.out.println(redisTemplate);
        ValueOperations valueOperations = redisTemplate.opsForValue();  //操作字符串类型
        HashOperations hashOperations = redisTemplate.opsForHash();  //操作哈希类型
        ListOperations listOperations = redisTemplate.opsForList();  //操作列表list类型
        SetOperations setOperations = redisTemplate.opsForSet();   //操作无需集合
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();  //操作有序集合
    }
}
