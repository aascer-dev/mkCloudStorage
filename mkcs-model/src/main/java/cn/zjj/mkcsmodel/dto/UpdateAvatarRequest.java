package cn.zjj.mkcsmodel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户头像请求DTO
 * 注意：此DTO已废弃，现在使用 MultipartFile 直接上传
 * @deprecated 使用 MultipartFile 参数代替
 */
@Data
@Deprecated
public class UpdateAvatarRequest {
    
    /**
     * 头像URL
     */
    @NotBlank(message = "头像URL不能为空")
    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatarUrl;
}
