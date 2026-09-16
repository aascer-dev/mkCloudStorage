package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个分片上传的 multipart 元数据。
 *
 * <p>分片二进制内容由 Controller 的 {@code MultipartFile chunk} 参数流式接收。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单个分片上传请求")
public class ChunkPartUploadRequest {

    @NotBlank(message = "上传任务ID不能为空")
    @Schema(description = "初始化接口返回的上传任务ID")
    private String uploadId;

    @NotNull(message = "分片索引不能为空")
    @PositiveOrZero(message = "分片索引不能小于0")
    @Schema(description = "分片索引（从0开始）")
    private Integer chunkIndex;

    @Schema(description = "分片内容SHA256 hash，可选")
    private String chunkHash;

    @PositiveOrZero(message = "随机校验起始位置不能小于0")
    @Schema(description = "随机位置校验的起始字节位置，可选")
    private Long randomOffset;

    @Positive(message = "随机校验长度必须大于0")
    @Schema(description = "随机位置校验字节长度，可选")
    private Integer randomLength;

    @Schema(description = "随机位置校验MD5 hash，可选")
    private String randomHash;

    @AssertTrue(message = "随机位置校验参数必须同时提供或同时省略")
    @Schema(hidden = true)
    public boolean isRandomChecksumComplete() {
        boolean noChecksum = randomOffset == null && randomLength == null && randomHash == null;
        boolean completeChecksum = randomOffset != null
                && randomLength != null
                && randomHash != null
                && !randomHash.isBlank();
        return noChecksum || completeChecksum;
    }
}
