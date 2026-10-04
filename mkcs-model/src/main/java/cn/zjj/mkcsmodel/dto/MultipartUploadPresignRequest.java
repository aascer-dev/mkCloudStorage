package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "获取 Multipart 单分片预签名地址请求")
public class MultipartUploadPresignRequest {
    @NotNull(message = "上传任务ID不能为空")
    private Long uploadId;

    @NotNull(message = "分片编号不能为空")
    @Min(value = 1, message = "分片编号从1开始")
    @Max(value = 10000, message = "分片编号不能超过10000")
    private Integer partNumber;
}
