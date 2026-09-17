package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Multipart 秒传随机切片验证请求")
public class MultipartSecondUploadVerifyRequest {

    @NotNull(message = "上传任务ID不能为空")
    private Long uploadId;

    @NotBlank(message = "随机切片MD5不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "随机切片MD5格式不正确")
    private String challengeHash;
}
