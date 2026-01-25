package cn.zjj.mkcsserver.config;

import com.zjj.mkcscommon.properties.MinIOProperties;
import com.zjj.mkcscommon.utils.MinIOUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinIOConfiguration {

    /**
     * 创建MinIOUtil对象
     * @param minIOProperties
     * @return MinIOUtil
     */
    @Bean
    @ConditionalOnMissingBean
    public MinIOUtil minioUtil(MinIOProperties minIOProperties) {
        log.info("开始创建MinIOUtil对象，配置参数：{}", minIOProperties);
        return new MinIOUtil(
                minIOProperties.getEndpoint(),
                minIOProperties.getAccessKey(),
                minIOProperties.getSecretKey(),
                minIOProperties.getBucketName()
        );
    }
}
