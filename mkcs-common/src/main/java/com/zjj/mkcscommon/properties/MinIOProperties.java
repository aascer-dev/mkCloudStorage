package com.zjj.mkcscommon.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置属性
 */
@Component
@Data
@ConfigurationProperties(prefix = "mkcs.minio")
public class MinIOProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
}
