package cn.zjj.mkcsmodel.vo;

import cn.zjj.mkcsmodel.entity.Files;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "文件或文件夹摘要")
public class FileSummaryResponse {

    private Long id;
    private Long bucketId;
    private String filename;
    private Long parentId;
    private Boolean isFolder;
    @Schema(description = "当前登录用户是否已收藏；匿名访问时为 false")
    private Boolean isFavorite;
    private Long size;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FileSummaryResponse from(Files file) {
        return from(file, false);
    }

    public static FileSummaryResponse from(Files file, boolean isFavorite) {
        return FileSummaryResponse.builder()
                .id(file.getId())
                .bucketId(file.getBucketId())
                .filename(file.getFilename())
                .parentId(file.getParentId())
                .isFolder(file.getIsFolder())
                .isFavorite(isFavorite)
                .size(file.getSize())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}
