package cn.zjj.mkcsserver.handler;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsserver.config.CorsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Applies API authentication, CORS, and unified authentication-error responses.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SaTokenExceptionHandler {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "X-Requested-With");

    private final ObjectMapper objectMapper;
    private final CorsProperties corsProperties;

    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .setAuth(obj -> SaRouter.match("/api/**")
                        .notMatch("/api/auth/**")
                        .check(r -> StpUtil.checkLogin()))
                .setError(e -> {
                    if (e instanceof BackResultException) {
                        return e.getMessage();
                    }

                    SaHolder.getResponse()
                            .setStatus(ResultCode.UNAUTHORIZED.getCode())
                            .setHeader("Content-Type", "application/json;charset=UTF-8");
                    log.warn("认证请求被拒绝: path={}, exceptionType={}",
                            SaHolder.getRequest().getRequestPath(), e.getClass().getSimpleName());
                    try {
                        return objectMapper.writeValueAsString(Result.error(ResultCode.UNAUTHORIZED));
                    } catch (Exception ex) {
                        log.error("认证失败响应序列化失败", ex);
                        return "{\"code\":401,\"message\":\"未授权\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
                    }
                })
                .setBeforeAuth(obj -> {
                    applyCorsHeaders();
                    SaRouter.match(SaHttpMethod.OPTIONS).back();
                });
    }

    private void applyCorsHeaders() {
        String origin = SaHolder.getRequest().getHeader("Origin");
        if (!corsProperties.isAllowedOrigin(origin)) {
            return;
        }

        SaHolder.getResponse()
                .setHeader("Access-Control-Allow-Origin", origin)
                .setHeader("Access-Control-Allow-Credentials", "true")
                .setHeader("Access-Control-Allow-Methods", String.join(", ", ALLOWED_METHODS))
                .setHeader("Access-Control-Allow-Headers", String.join(", ", ALLOWED_HEADERS))
                .setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .setHeader("Access-Control-Max-Age", "3600")
                .setHeader("Vary", "Origin");
    }
}
