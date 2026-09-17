package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.BucketInfoDTO;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsmodel.entity.StorageBuckets;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 存储桶管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/storage-buckets")
@Tag(name = "存储桶管理", description = "存储桶相关接口")
@RequiredArgsConstructor
public class StorageBucketsController {

    private final StorageBucketsService storageBucketsService;

    /**
     * 创建存储桶
     */
    @PostMapping
    @Operation(summary = "创建存储桶", description = "创建新的存储桶")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "409", description = "存储桶名称已存在")
    })
    public Result<StorageBuckets> createBucket(
            @Valid @RequestBody CreateBucketRequest request) {
        
        Long userId = UserContext.requireUserId();
        StorageBuckets bucket = storageBucketsService.createBucket(
                request.getBucketName(), 
                request.getDescription(), 
                userId
        );
        
        return Result.success("存储桶创建成功", bucket);
    }

    /**
     * 删除存储桶
     */
    @DeleteMapping("/{bucketId}")
    @Operation(summary = "删除存储桶", description = "删除指定的存储桶")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "存储桶不存在"),
        @ApiResponse(responseCode = "403", description = "无权限删除")
    })
    public Result<String> deleteBucket(
            @Parameter(description = "存储桶ID") @PathVariable @NotNull @Positive Long bucketId) {
        
        Long userId = UserContext.requireUserId();
        boolean deleted = storageBucketsService.deleteBucket(bucketId, userId);
        
        if (deleted) {
            return Result.success("存储桶删除成功");
        } else {
            return Result.error("存储桶删除失败");
        }
    }

    /**
     * 获取当前用户的存储桶列表
     */
    @GetMapping("/my")
    @Operation(summary = "获取我的存储桶", description = "获取当前用户的所有存储桶")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功")
    })
    public Result<List<StorageBuckets>> getMyBuckets() {
        Long userId = UserContext.requireUserId();
        List<StorageBuckets> buckets = storageBucketsService.getBucketsByUserId(userId);
        return Result.success("获取存储桶列表成功", buckets);
    }

    /**
     * 分页查询存储桶
     */
    @GetMapping
    @Operation(summary = "分页查询存储桶", description = "分页查询存储桶列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    public Result<IPage<StorageBuckets>> getBucketPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Positive Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Positive Integer pageSize,
            @Parameter(description = "存储桶名称") @RequestParam(required = false) String bucketName,
            @Parameter(description = "状态") @RequestParam(required = false) Byte status,
            @Parameter(description = "开始时间") @RequestParam(required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        
        Long userId = UserContext.requireUserId();
        IPage<StorageBuckets> page = storageBucketsService.getBucketPage(
                pageNum, pageSize, userId, bucketName, status, startTime, endTime);
        
        return Result.success("查询存储桶列表成功", page);
    }

    /**
     * 获取存储桶详情
     */
    @GetMapping("/{bucketId}")
    @Operation(summary = "获取存储桶详情", description = "根据ID获取存储桶详细信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "404", description = "存储桶不存在")
    })
    public Result<StorageBuckets> getBucketById(
            @Parameter(description = "存储桶ID") @PathVariable @NotNull @Positive Long bucketId) {
        
        StorageBuckets bucket = storageBucketsService.getById(bucketId);
        if (bucket == null) {
            return Result.error("存储桶不存在");
        }
        
        // 检查权限
        Long userId = UserContext.requireUserId();
        if (!bucket.getOwnerId().equals(userId)) {
            return Result.error("无权限访问该存储桶");
        }
        
        return Result.success("获取存储桶详情成功", bucket);
    }

    /**
     * 更新存储桶信息
     */
    @PutMapping("/{bucketId}")
    @Operation(summary = "更新存储桶", description = "更新存储桶信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "404", description = "存储桶不存在"),
        @ApiResponse(responseCode = "403", description = "无权限更新")
    })
    public Result<StorageBuckets> updateBucket(
            @Parameter(description = "存储桶ID") @PathVariable @NotNull @Positive Long bucketId,
            @Valid @RequestBody UpdateBucketRequest request) {
        
        // 检查存储桶是否存在
        StorageBuckets existingBucket = storageBucketsService.getById(bucketId);
        if (existingBucket == null) {
            return Result.error("存储桶不存在");
        }
        
        // 检查权限
        Long userId = UserContext.requireUserId();
        if (!existingBucket.getOwnerId().equals(userId)) {
            return Result.error("无权限更新该存储桶");
        }
        
        // 更新信息
        StorageBuckets updateBucket = new StorageBuckets();
        updateBucket.setId(bucketId);
        updateBucket.setDescription(request.getDescription());
        
        StorageBuckets updatedBucket = storageBucketsService.updateBucket(updateBucket);
        return Result.success("存储桶更新成功", updatedBucket);
    }

    /**
     * 设置默认存储桶
     */
    @PostMapping("/{bucketId}/set-default")
    @Operation(summary = "设置默认存储桶", description = "将指定存储桶设置为默认存储桶")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "设置成功"),
        @ApiResponse(responseCode = "404", description = "存储桶不存在"),
        @ApiResponse(responseCode = "403", description = "无权限设置")
    })
    public Result<String> setDefaultBucket(
            @Parameter(description = "存储桶ID") @PathVariable @NotNull @Positive Long bucketId) {
        
        Long userId = UserContext.requireUserId();
        boolean result = storageBucketsService.setDefaultBucket(userId, bucketId);
        
        if (result) {
            return Result.success("默认存储桶设置成功");
        } else {
            return Result.error("默认存储桶设置失败");
        }
    }

    /**
     * 获取默认存储桶
     */
    @GetMapping("/default")
    @Operation(summary = "获取默认存储桶", description = "获取当前用户的默认存储桶")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功")
    })
    public Result<StorageBuckets> getDefaultBucket() {
        Long userId = UserContext.requireUserId();
        StorageBuckets defaultBucket = storageBucketsService.getDefaultBucket(userId);
        
        if (defaultBucket != null) {
            return Result.success("获取默认存储桶成功", defaultBucket);
        } else {
            return Result.success("未设置默认存储桶", null);
        }
    }

    /**
     * 获取当前用户的存储桶摘要信息（优化端点）
     * 解决前端登录后并发请求导致的重复查询问题
     */
    @GetMapping("/info")
    @Operation(summary = "获取存储桶摘要信息", description = "一次请求获取用户所有存储桶和默认存储桶，避免并发查询")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功")
    })
    public Result<BucketInfoDTO> getBucketInfo() {
        Long userId = UserContext.requireUserId();
        
        // 一次查询获取所有存储桶
        List<StorageBuckets> allBuckets = storageBucketsService.getBucketsByUserId(userId);
        
        // 从结果中取第一个作为默认桶
        StorageBuckets defaultBucket = allBuckets.isEmpty() ? null : allBuckets.getFirst();
        
        BucketInfoDTO info = new BucketInfoDTO();
        info.setAllBuckets(allBuckets);
        info.setDefaultBucket(defaultBucket);
        info.setTotal(allBuckets.size());
        
        return Result.success("获取存储桶信息成功", info);
    }

    /**
     * 检查存储桶名称是否可用
     */
    @GetMapping("/check-name")
    @Operation(summary = "检查存储桶名称", description = "检查存储桶名称是否可用")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "检查成功")
    })
    public Result<Boolean> checkBucketName(
            @Parameter(description = "存储桶名称") @RequestParam @NotBlank String bucketName) {
        
        boolean available = storageBucketsService.isBucketNameAvailable(bucketName, null);
        return Result.success("检查存储桶名称完成", available);
    }

    /**
     * 同步MinIO存储桶
     */
    @PostMapping("/sync")
    @Operation(summary = "同步存储桶", description = "从MinIO同步存储桶到数据库")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "同步成功")
    })
    public Result<Integer> syncBuckets() {
        int syncCount = storageBucketsService.syncBucketsFromMinIO();
        return Result.success("存储桶同步完成", syncCount);
    }

    /**
     * 创建存储桶请求
     */
    public static class CreateBucketRequest {
        @NotBlank(message = "存储桶名称不能为空")
        private String bucketName;
        
        private String description;

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * 更新存储桶请求
     */
    public static class UpdateBucketRequest {
        private String description;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
