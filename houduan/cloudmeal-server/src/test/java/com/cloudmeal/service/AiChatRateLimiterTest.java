package com.cloudmeal.service;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatRateLimiterTest {

    @Test
    void shouldLimitEleventhRequestButIsolateUsersAndRecoverAfterWindow() {
        AtomicLong nowMillis = new AtomicLong(0);
        StringRedisTemplate redis = simulatedRedis(nowMillis);
        AiChatRateLimiter limiter = new AiChatRateLimiter(redis, 10, 60);

        for (int i = 0; i < 10; i++) {
            limiter.check(7L);
        }
        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED);
        limiter.check(8L);

        nowMillis.set(59_999);
        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED);
        nowMillis.set(60_000);
        limiter.check(7L);
    }

    @Test
    void shouldUseOneAtomicRedisScriptWithExpiry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60"))).thenReturn(1L);
        new AiChatRateLimiter(redis, 10, 60).check(7L);

        org.mockito.ArgumentCaptor<RedisScript> script = org.mockito.ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(java.util.Collections.singletonList("ai:chat:rate:v1:user:7")), eq("60"));
        String source = script.getValue().getScriptAsString();
        assertThat(source).contains("redis.call('INCR', KEYS[1])")
                .contains("if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end")
                .contains("return count");
    }

    @Test
    void shouldAllowOnlyTenConcurrentRequestsForOneUser() {
        AiChatRateLimiter limiter = new AiChatRateLimiter(simulatedRedis(new AtomicLong(0)), 10, 60);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<CompletableFuture<Boolean>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        limiter.check(7L);
                        return true;
                    } catch (BaseException ex) {
                        assertThat(ex).hasMessage(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED);
                        return false;
                    }
                }, pool));
            }
            assertThat(calls.stream().filter(CompletableFuture::join).count()).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simulatedRedisShouldReturnEachConcurrentRequestsOwnCount() {
        StringRedisTemplate redis = simulatedRedis(new AtomicLong(0));
        RedisScript<Long> script = mock(RedisScript.class);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<CompletableFuture<Long>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> redis.execute(
                        script, java.util.Collections.singletonList("ai:chat:rate:v1:user:7"), "60"), pool));
            }
            assertThat(calls.stream().map(CompletableFuture::join).sorted().toList())
                    .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldFailClosedWithoutExposingRedisException() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60")))
                .thenThrow(new IllegalStateException("redis://internal-host:6379 secret"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(redis, 10, 60);

        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldFailClosedOnMissingRedisResultAndRejectMissingIdentity() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AiChatRateLimiter limiter = new AiChatRateLimiter(redis, 10, 60);
        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> limiter.check(null))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.USER_NOT_LOGIN);
    }

    private StringRedisTemplate simulatedRedis(AtomicLong nowMillis) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class)))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    long ttlMillis = Long.parseLong(invocation.getArgument(2)) * 1000;
                    long[] requestCount = new long[1];
                    windows.compute(keys.get(0), (key, old) -> {
                        if (old == null || nowMillis.get() >= old.expiresAt) {
                            requestCount[0] = 1;
                            return new Window(1, nowMillis.get() + ttlMillis);
                        }
                        requestCount[0] = old.count.incrementAndGet();
                        return old;
                    });
                    return requestCount[0];
                });
        return redis;
    }

    private static class Window {
        final AtomicInteger count;
        final long expiresAt;

        Window(int count, long expiresAt) {
            this.count = new AtomicInteger(count);
            this.expiresAt = expiresAt;
        }
    }
}
