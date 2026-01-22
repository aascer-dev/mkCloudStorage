package com.zjj.mkcs;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;  // ← 必须加这个 import

public class Generator {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/mkCloudStorage?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&useSSL=false";
        String username = "root";
        String password = "123456";  // ← 改成你的真实密码！！！

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> {
                    builder
                            .author("zjj")
                            // .enableSwagger()  // ← 如果不想生成 Swagger 注解，就不写这一行（默认关闭）
                            .outputDir("src/main/java")
                            .commentDate("yyyy-MM-dd");
                })
                .packageConfig(builder -> {
                    builder
                            .parent("com.zjj.mkcs")
                            .entity("pojo.entity")
                            .mapper("server.mapper")               // 统一 mapper 包
                            .service("server.service")
                            .serviceImpl("server.service.impl")
                            .xml("mapper")
                            .controller("server.controller")
                            .pathInfo(Collections.singletonMap(
                                    OutputFile.xml, "src/main/resources/mapper"  // XML 输出到 resources/mapper
                            ));
                })
                .strategyConfig(builder -> {
                    builder
                            // 全表生成（无统一前缀时默认这样）
                            // 如果想测试只生成部分表：取消注释下面这行
                            // .addInclude("users", "files", "file_favorites")

                            .entityBuilder()
                            .naming(NamingStrategy.underline_to_camel)
                            .columnNaming(NamingStrategy.underline_to_camel)
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .logicDeleteColumnName("deleted")
                            .idType(com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID)
                            .formatFileName("%s")

                            .mapperBuilder()
                            .formatMapperFileName("%sMapper")
                            .formatXmlFileName("%sMapper")

                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

        System.out.println("代码生成完成！");
    }
}