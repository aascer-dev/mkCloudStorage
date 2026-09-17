package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Multipart 上传初始化响应")
public class MultipartUploadInitResponse {
    private Long uploadId;
    private Integer partSize;
    private Integer totalParts;
    private Boolean instantUpload;
    private Long fileId;
    @Schema(description = "是否需要提交本地随机切片摘要以完成秒传")
    private Boolean secondUploadChallenge;
    @Schema(description = "随机切片起始字节位置，仅secondUploadChallenge为true时返回")
    private Long challengeOffset;
    @Schema(description = "随机切片长度，仅secondUploadChallenge为true时返回")
    private Integer challengeLength;
}
