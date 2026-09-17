package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "收藏文件响应")
public class FavoriteFileResponse {

    private Long id;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private FileSummaryResponse file;
}
