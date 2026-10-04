package cn.zjj.mkcsserver;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import cn.zjj.mkcsserver.auth.AuthProperties;

/**
 * @author zjj
 */
@SpringBootApplication(scanBasePackages = {"cn.zjj.mkcsserver", "com.zjj.mkcscommon"})
@EnableConfigurationProperties(AuthProperties.class)
@MapperScan("cn.zjj.mkcsserver.mapper")
@Slf4j
public class MkcsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MkcsServerApplication.class, args);
        log.info("MKCS Server started successfully.");
    }

}
