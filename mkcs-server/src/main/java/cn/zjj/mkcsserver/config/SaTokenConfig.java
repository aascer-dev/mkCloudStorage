package cn.zjj.mkcsserver.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    
    /**
     * 注册Sa-Token拦截器
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 注册Sa-Token拦截器，校验规则为StpUtil.checkLogin()登录校验
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 指定一条match规则
            SaRouter
                .match("/**")    // 拦截的path列表，可以写多个
                .notMatch("/api/auth/**")    // 排除掉的path列表，可以写多个  
                .notMatch("/api/example/**") // 排除示例接口
                .notMatch("/error")          // 排除错误页面
                .notMatch("/favicon.ico")    // 排除网站图标
                // SpringDoc OpenAPI 相关路径排除
                .notMatch("/swagger-ui/**")  // 排除 Swagger UI
                .notMatch("/swagger-ui.html") // 排除 Swagger UI 页面
                .notMatch("/v3/api-docs/**") // 排除 OpenAPI 文档
                .notMatch("/v3/api-docs")    // 排除 OpenAPI 文档根路径
                .notMatch("/swagger-resources/**") // 排除 Swagger 资源
                .notMatch("/webjars/**")     // 排除静态资源
                .check(r -> StpUtil.checkLogin());        // 要执行的校验动作，可以写完整的lambda表达式
        })).addPathPatterns("/**");
    }
}