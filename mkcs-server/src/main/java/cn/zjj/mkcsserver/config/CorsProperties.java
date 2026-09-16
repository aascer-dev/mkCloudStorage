package cn.zjj.mkcsserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mkcs.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }
}
