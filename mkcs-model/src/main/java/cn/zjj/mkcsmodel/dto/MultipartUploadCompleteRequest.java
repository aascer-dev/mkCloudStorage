package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "完成对象存储 Multipart 上传请求")
public class MultipartUploadCompleteRequest {
    @NotNull(message = "上传任务ID不能为空")
    private Long uploadId;

    @NotEmpty(message = "已上传分片不能为空")
    @Valid
    private List<MultipartUploadPartRequest> parts;
}
