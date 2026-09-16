package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片上传响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分片上传响应")
public class ChunkUploadResponse {

    @Schema(description = "上传任务ID")
    private String uploadId;

    @Schema(description = "当前分片索引")
    private Integer chunkIndex;

    @Schema(description = "总分片数")
    private Integer totalChunks;

    @Schema(description = "已上传的分片索引列表")
    private List<Integer> uploadedChunks;

    @Schema(description = "上传进度百分比")
    private Integer progress;

    @Schema(description = "是否上传完成")
    private Boolean isComplete;

    @Schema(description = "文件ID（上传完成时返回）")
    private Long fileId;

    @Schema(description = "文件访问URL（上传完成时返回）")
    private String fileUrl;

    @Schema(description = "消息")
    private String message;
}
