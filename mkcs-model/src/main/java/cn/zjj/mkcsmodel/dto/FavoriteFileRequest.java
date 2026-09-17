package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "收藏文件或文件夹请求")
public class FavoriteFileRequest {

    @Size(max = 255, message = "收藏备注不能超过255个字符")
    @Schema(description = "可选收藏备注")
    private String notes;
}
