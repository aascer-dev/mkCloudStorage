package cn.zjj.mkcsserver;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * 用于生成实体类、Mapper、Service、Controller等代码
 */
public class Generator {
    
    public static void main(String[] args) {
        // 数据库连接配置
        String url = "jdbc:mysql://localhost:3306/mkCloudStorage?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8";
        String username = "root";
        String password = "123456";
        
        // 项目路径
        String projectPath = System.getProperty("user.dir");
        String modulePath = projectPath + "/mkcs-server";
        
        FastAutoGenerator.create(url, username, password)
                // 全局配置
                .globalConfig(builder -> {
                    builder.author("zjj") // 设置作者
                            .enableSwagger() // 开启 swagger 模式
                            .outputDir(modulePath + "/src/main/java") // 指定输出目录
                            .disableOpenDir(); // 禁止打开输出目录
                })
                // 数据源配置
                .dataSourceConfig(builder -> builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                    int typeCode = metaInfo.getJdbcType().TYPE_CODE;
                    if (typeCode == java.sql.Types.SMALLINT) {
                        // 自定义类型转换
                        return DbColumnType.INTEGER;
                    }
                    return typeRegistry.getColumnType(metaInfo);
                }))
                // 包配置
                .packageConfig(builder -> {
                    builder.parent("cn.zjj.mkcsserver") // 设置父包名
                            .moduleName("") // 设置父包模块名
                            .entity("entity") // 实体类包名
                            .service("service") // Service 包名
                            .serviceImpl("service.impl") // Service Impl 包名
                            .mapper("mapper") // Mapper 包名
                            .xml("mapper.xml") // Mapper XML 包名
                            .controller("controller") // Controller 包名
                            .pathInfo(Collections.singletonMap(OutputFile.xml, modulePath + "/src/main/resources/mapper")); // 设置mapperXml生成路径
                })
                // 策略配置
                .strategyConfig(builder -> {
                    builder.addInclude() // 设置需要生成的表名
                            //.addTablePrefix("t_", "c_") // 设置过滤表前缀
                            // 实体类策略配置
                            .entityBuilder()
                            .enableLombok() // 开启 lombok 模式
                            .enableTableFieldAnnotation() // 开启生成实体时生成字段注解
                            .enableFileOverride() // 覆盖已生成文件
                            // Controller策略配置
                            .controllerBuilder()
                            .enableRestStyle() // 开启生成@RestController 控制器
                            // Service策略配置
                            .serviceBuilder()
                            .formatServiceFileName("%sService") // 格式化 service 接口文件名称
                            .formatServiceImplFileName("%sServiceImpl") // 格式化 service 实现类文件名称
                            .enableFileOverride() // 覆盖已生成文件
                            // Mapper策略配置
                            .mapperBuilder()
                            .enableMapperAnnotation() // 开启 @Mapper 注解
                            .enableFileOverride(); // 覆盖已生成文件
                })
                // 模板引擎配置，默认 Velocity 可选模板引擎 Beetl 或 Freemarker
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}