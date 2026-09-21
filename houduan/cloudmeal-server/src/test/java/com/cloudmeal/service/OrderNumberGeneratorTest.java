package com.cloudmeal.service;

import com.cloudmeal.exception.OrderBusinessException;
import com.cloudmeal.service.impl.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderNumberGeneratorTest {
    private ValueOperations<String, String> values;
    private OrderNumberGenerator generator;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        generator = new OrderNumberGenerator(redis);
    }

    @Test
    void generatesEighteenDigitNumericNumbersAndKeepsSequenceUnique() {
        AtomicLong counter = new AtomicLong();
        when(values.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String number = generator.nextNumber();
            assertTrue(number.matches("9\\d{17}"));
            assertTrue(numbers.add(number));
        }
        assertEquals("900000000000000001", generatorNumber(1));
        assertEquals(10_000, numbers.size());
    }

    @Test
    void redisUnavailableFailsClosed() {
        when(values.increment(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        assertThrows(OrderBusinessException.class, generator::nextNumber);
    }

    @Test
    void missingOrOutOfRangeSequenceFailsClosed() {
        when(values.increment(anyString())).thenReturn(null, 0L, 100_000_000_000_000_000L);
        assertThrows(OrderBusinessException.class, generator::nextNumber);
        assertThrows(OrderBusinessException.class, generator::nextNumber);
        assertThrows(OrderBusinessException.class, generator::nextNumber);
    }

    private String generatorNumber(long sequence) {
        when(values.increment(anyString())).thenReturn(sequence);
        return generator.nextNumber();
    }
}
