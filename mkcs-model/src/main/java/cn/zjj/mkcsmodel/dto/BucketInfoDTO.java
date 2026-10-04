package cn.zjj.mkcsmodel.dto;

import cn.zjj.mkcsmodel.entity.StorageBuckets;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 存储桶摘要信息DTO
 * 一次请求获取用户所有存储桶和默认存储桶的信息，避免并发查询
 *
 * @author zjj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "存储桶摘要信息")
public class BucketInfoDTO {

    @Schema(description = "用户所有存储桶列表")
    private List<StorageBuckets> allBuckets;

    @Schema(description = "默认存储桶（通常是第一个创建的桶）")
    private StorageBuckets defaultBucket;

    @Schema(description = "用户拥有的存储桶总数")
    private Integer total;
}
