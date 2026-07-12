package com.archimstechcorner.marketplace.shared.security.ratelimit;

import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.error.JsonErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * A generic rate limiting interceptor backed by Redis.
 * Uses a simple fixed window Lua script to prevent brute forcing
 * on sensitive endpoints like Login and Registration.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final JsonErrorWriter errorWriter;

    // e.g., 5 requests per 60 seconds
    private static final int MAX_REQUESTS = 5;
    private static final int WINDOW_SECONDS = 60;

    // Simple fixed-window rate limiter in Lua
    private static final String LUA_SCRIPT = 
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local current = redis.call('get', key) " +
            "if current and tonumber(current) >= limit then " +
            "   return 0 " +
            "end " +
            "current = redis.call('incr', key) " +
            "if tonumber(current) == 1 then " +
            "   redis.call('expire', key, window) " +
            "end " +
            "return 1";

    public RateLimitInterceptor(StringRedisTemplate redisTemplate, JsonErrorWriter errorWriter) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        this.errorWriter = errorWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String key = "rate_limit:" + request.getRequestURI() + ":" + clientIp;

        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(MAX_REQUESTS),
                String.valueOf(WINDOW_SECONDS)
        );

        if (result != null && result == 0L) {
            log.warn("Rate limit exceeded for IP {} on URI {}", clientIp, request.getRequestURI());
            // Use the standard error envelope so clients get a consistent {error:{code,message,...}} shape.
            errorWriter.write(response, ErrorCode.RATE_LIMITED, "Too many requests. Please try again later.");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
