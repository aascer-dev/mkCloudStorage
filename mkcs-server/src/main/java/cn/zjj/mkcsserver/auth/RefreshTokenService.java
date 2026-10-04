package cn.zjj.mkcsserver.auth;

import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "mkcs:refresh:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            if value ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public String create(AuthenticatedUser user) {
        String token = newToken();
        redisTemplate.opsForValue().set(key(token), value(user), authProperties.refreshTokenTtl());
        return token;
    }

    public RotatedRefreshToken rotate(String refreshToken) {
        String expectedValue = readValue(refreshToken);
        AuthenticatedUser user = parseValue(expectedValue);
        String replacement = newToken();
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(refreshToken), key(replacement)),
                expectedValue,
                value(user),
                Long.toString(authProperties.refreshTokenTtl().toSeconds())
        );
        if (!Long.valueOf(1).equals(rotated)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return new RotatedRefreshToken(user, replacement);
    }

    public void revoke(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(key(refreshToken));
        }
    }

    public record RotatedRefreshToken(AuthenticatedUser user, String refreshToken) {
    }

    private String readValue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Object value = redisTemplate.opsForValue().get(key(refreshToken));
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return stringValue;
    }

    private AuthenticatedUser parseValue(String value) {
        String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        try {
            return new AuthenticatedUser(Long.valueOf(parts[0]), parts[1], Long.valueOf(parts[2]));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    private String value(AuthenticatedUser user) {
        return user.userId() + ":" + user.username() + ":" + user.tokenVersion();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String refreshToken) {
        return KEY_PREFIX + sha256(refreshToken);
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
