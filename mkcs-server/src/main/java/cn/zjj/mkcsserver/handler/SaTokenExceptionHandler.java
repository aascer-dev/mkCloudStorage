package cn.zjj.mkcsserver.handler;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.StopMatchException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.enumeration.ResultCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token过滤器配置，用于统一异常处理
 */
@Configuration
public class SaTokenExceptionHandler {

    //TODO详细配置satoken的认证和异常处理
    /**
     * 注册Sa-Token全局过滤器
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
            // 指定[拦截路由]与[放行路由]
            .addInclude("/**")
            //.addExclude("/favicon.ico")
            .addExclude("/swagger-ui/**")    // 排除 Swagger UI
            .addExclude("/swagger-ui.html")  // 排除 Swagger UI 页面
            .addExclude("/v3/api-docs/**")   // 排除 OpenAPI 文档
            .addExclude("/v3/api-docs")      // 排除 OpenAPI 文档根路径
            .addExclude("/swagger-resources/**") // 排除 Swagger 资源
            .addExclude("/webjars/**")       // 排除静态资源
            .addExclude("/static/**")        // 排除 static 目录
            .addExclude("/*.html")           // 排除根目录下的 HTML 文件
            .addExclude("/*.css")            // 排除根目录下的 CSS 文件
            .addExclude("/*.js")             // 排除根目录下的 JS 文件

            
            // 认证函数: 每次请求执行
            .setAuth(obj -> {
                SaRouter.match("/**")
                    .notMatch("/api/auth/**")    // 排除认证相关接口
                    .notMatch("/error")          // 排除错误页面
                    //.notMatch("/favicon.ico")    // 排除网站图标
                    // SpringDoc OpenAPI 相关路径排除
                    .notMatch("/swagger-ui/**")  // 排除 Swagger UI
                    .notMatch("/swagger-ui.html") // 排除 Swagger UI 页面
                    .notMatch("/v3/api-docs/**") // 排除 OpenAPI 文档
                    .notMatch("/v3/api-docs")    // 排除 OpenAPI 文档根路径
                    .notMatch("/swagger-resources/**") // 排除 Swagger 资源
                    .notMatch("/webjars/**")     // 排除静态资源
                    // 静态资源排除
                    .notMatch("/static/**")      // 排除 static 目录
                    .notMatch("/*.html")         // 排除根目录下的 HTML 文件
                    .notMatch("/*.css")          // 排除根目录下的 CSS 文件
                    .notMatch("/*.js")           // 排除根目录下的 JS 文件
                    .check(r -> StpUtil.checkLogin());
            })
            
            // 异常处理函数：每次[认证函数]发生异常时执行此函数
            .setError(e -> {
                // 设置响应头
                SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=UTF-8");
                
                // 创建统一的错误响应
                Result<Void> result;
                if (e instanceof BackResultException) {
                    // 这种异常不需要处理，直接返回
                    return e.getMessage();
                } else if (e instanceof StopMatchException) {
                    // 停止匹配异常，返回未授权
                    result = Result.error(ResultCode.UNAUTHORIZED);
                } else {
                    // 其他异常统一返回未授权
                    result = Result.error(ResultCode.UNAUTHORIZED.getCode(), "认证失败: " + e.getMessage());
                }
                
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.writeValueAsString(result);
                } catch (Exception ex) {
                    return "{\"code\":401,\"message\":\"认证失败\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
                }
            })
            
            // 前置函数：在每次[认证函数]之前执行
            .setBeforeAuth(obj -> {
                // 设置跨域响应头
                SaHolder.getResponse()
                    .setHeader("Access-Control-Allow-Origin", "*")
                    .setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .setHeader("Access-Control-Allow-Headers", "*")
                    .setHeader("Access-Control-Max-Age", "3600");
                
                // 如果是预检请求，则立即返回成功
                SaRouter.match(SaHttpMethod.OPTIONS)
                    .free(r -> System.out.println("--------OPTIONS预检请求，不做处理"))
                    .back();
            });
    }
}