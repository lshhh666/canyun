package com.cloudmeal.service.impl;

import com.cloudmeal.exception.OrderBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** A persistent, shared Redis counter produces 18-digit order numbers. */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {
    private static final String SEQUENCE_KEY = "orders:number:sequence:v1";
    private static final long MAX_SEQUENCE = 99_999_999_999_999_999L;

    private final StringRedisTemplate redisTemplate;

    public String nextNumber() {
        final Long sequence;
        try {
            sequence = redisTemplate.opsForValue().increment(SEQUENCE_KEY);
        } catch (DataAccessException ex) {
            throw new OrderBusinessException("订单号生成失败，请稍后重试");
        }
        if (sequence == null || sequence <= 0 || sequence > MAX_SEQUENCE) {
            throw new OrderBusinessException("订单号生成失败，请稍后重试");
        }
        // Legacy numbers begin with a year (20xx); 9 reserves a separate namespace.
        return String.format(Locale.ROOT, "9%017d", sequence);
    }
}
