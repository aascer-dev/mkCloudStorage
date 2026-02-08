package cn.zjj.mkcsserver.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zjj.mkcscommon.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC Configuration
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    // Sa-Token interceptor is configured in SaTokenConfig, no need to repeat here

    /**
     * Configure static resource mapping
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Configure static resource access
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        // Swagger UI static resource mapping
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * Configure character encoding for String converter
     */
    @Bean
    public StringHttpMessageConverter stringHttpMessageConverter() {
        StringHttpMessageConverter converter = new StringHttpMessageConverter();
        converter.setDefaultCharset(StandardCharsets.UTF_8);

        // Support multiple media types with UTF-8 encoding
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(new MediaType("text", "plain", StandardCharsets.UTF_8));
        supportedMediaTypes.add(new MediaType("text", "html", StandardCharsets.UTF_8));
        supportedMediaTypes.add(new MediaType("application", "json", StandardCharsets.UTF_8));
        supportedMediaTypes.add(new MediaType("application", "*+json", StandardCharsets.UTF_8));
        converter.setSupportedMediaTypes(supportedMediaTypes);

        return converter;
    }

    /**
     * Json 转换器
     */
    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        // 1. 获取你的自定义 Mapper
        JacksonObjectMapper objectMapper = new JacksonObjectMapper();

        // 2. 手动注册 Long -> String 的序列化规则（这里主要是因为JavaScript的num（16位）不够大，需要把id（Long）转换成String）
        SimpleModule simpleModule = new SimpleModule();
        // 针对 Long 类
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        // 针对 long 基本类型
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        // 注册模块到你的 objectMapper
        objectMapper.registerModule(simpleModule);
        converter.setObjectMapper(objectMapper);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        // Support JSON media types with UTF-8 encoding
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(new MediaType("application", "json", StandardCharsets.UTF_8));
        supportedMediaTypes.add(new MediaType("application", "*+json", StandardCharsets.UTF_8));
        supportedMediaTypes.add(new MediaType("text", "json", StandardCharsets.UTF_8));
        converter.setSupportedMediaTypes(supportedMediaTypes);
        return converter;
    }

    /**
     * Configure message converters with proper encoding
     */
    @Override
    public void configureMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        log.info("Configuring message converters with UTF-8 encoding...");

        // Add UTF-8 string converter first
        converters.add(stringHttpMessageConverter());

        // Add UTF-8 JSON converter
        converters.add(mappingJackson2HttpMessageConverter());
    }
}
