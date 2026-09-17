package cn.zjj.mkcsserver.auth;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.service.UsersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/verification-code/send",
            "/api/auth/verification-code/verify",
            "/api/auth/verification-code/cooldown",
            "/api/auth/reset-password",
            "/api/auth/usernames/",
            "/api/auth/emails/",
            "/api/auth/oauth2/github/authorize",
            "/api/auth/oauth2/github/callback",
            "/api/auth/oauth2/send-verification-code",
            "/api/auth/oauth2/verify-email",
            "/api/auth/oauth2/complete-registration"
    );

    private final JwtProvider jwtProvider;
    private final UsersService usersService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (path.startsWith("/api/public/shares/") || path.startsWith("/api/files/preview/")) {
            return true;
        }
        return PUBLIC_AUTH_PATHS.stream().anyMatch(publicPath ->
                publicPath.endsWith("/") ? path.startsWith(publicPath) : path.equals(publicPath));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthenticatedUser tokenUser;
        try {
            tokenUser = jwtProvider.parseAccessToken(extractBearerToken(request));
        } catch (RuntimeException exception) {
            reject(response);
            return;
        }

        Users user = usersService.getById(tokenUser.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !tokenVersionOf(user).equals(tokenUser.tokenVersion())) {
            reject(response);
            return;
        }

        UserContext.set(tokenUser);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private Long tokenVersionOf(Users user) {
        return user.getTokenVersion() == null ? 1L : user.getTokenVersion();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        return authorization.substring(7);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.UNAUTHORIZED));
    }
}
