package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "已上传 Multipart 分片")
public class MultipartUploadPartRequest {
    @NotNull(message = "分片编号不能为空")
    @Min(value = 1, message = "分片编号从1开始")
    @Max(value = 10000, message = "分片编号不能超过10000")
    private Integer partNumber;

    @NotBlank(message = "分片ETag不能为空")
    private String etag;
}
