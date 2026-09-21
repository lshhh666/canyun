package com.cloudmeal.service;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/** One Redis key per authenticated user, with a window starting at the first request. */
@Component
public class AiChatRateLimiter {
    private static final String KEY_PREFIX = "ai:chat:rate:v1:user:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1])\n"
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
                    + "return count", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public AiChatRateLimiter(StringRedisTemplate redisTemplate,
                             @Value("${cloudmeal.ai.rate-limit.max-requests:10}") int maxRequests,
                             @Value("${cloudmeal.ai.rate-limit.window-seconds:60}") long windowSeconds) {
        if (maxRequests <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("AI chat rate limit must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public void check(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        final Long count;
        try {
            count = redisTemplate.execute(INCREMENT_SCRIPT,
                    Collections.singletonList(KEY_PREFIX + userId), String.valueOf(windowSeconds));
        } catch (RuntimeException ex) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (count == null || count <= 0) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (count > maxRequests) {
            throw new BaseException(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED);
        }
    }
}
