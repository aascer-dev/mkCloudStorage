package cn.zjj.mkcsserver.auth;

import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final AuthProperties authProperties;
    private SecretKey signingKey;

    @PostConstruct
    void initialize() {
        byte[] secretBytes = authProperties.secretKey().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("MKCS_JWT_SECRET_KEY must contain at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueAccessToken(AuthenticatedUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(authProperties.accessTokenTtl());
        return Jwts.builder()
                .issuer(authProperties.issuer())
                .subject(user.userId().toString())
                .claim("username", user.username())
                .claim("ver", user.tokenVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(authProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            Number version = claims.get("ver", Number.class);
            if (username == null || version == null) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
            return new AuthenticatedUser(userId, username, version.longValue());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    public long accessTokenTtlSeconds() {
        return authProperties.accessTokenTtl().toSeconds();
    }
}
