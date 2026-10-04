package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Multipart 分片预签名响应")
public class MultipartUploadPresignResponse {
    private Integer partNumber;
    private String url;
    private Integer expiresInSeconds;
}
