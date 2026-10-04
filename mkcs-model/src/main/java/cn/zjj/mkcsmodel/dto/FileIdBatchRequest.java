package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量文件操作请求")
public class FileIdBatchRequest {

    @Valid
    @NotEmpty(message = "文件ID不能为空")
    @Schema(description = "文件或文件夹ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotNull(message = "文件ID不能为空") Long> fileIds;
}
