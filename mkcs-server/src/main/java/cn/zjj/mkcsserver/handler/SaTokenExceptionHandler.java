package cn.zjj.mkcsserver.handler;

import cn.zjj.mkcsserver.config.CorsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    private final CorsProperties corsProperties;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                        .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
                        .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
                        .exposedHeaders("Content-Disposition", "Content-Length", "Content-Type")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
