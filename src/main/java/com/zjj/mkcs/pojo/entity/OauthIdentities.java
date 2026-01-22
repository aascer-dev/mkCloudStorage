package com.zjj.mkcs.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 第三方身份关联表
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("oauth_identities")
public class OauthIdentities implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联本地用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 平台标识：github, google 等
     */
    @TableField("provider")
    private String provider;

    /**
     * 第三方平台的唯一 ID，如 GitHub 数字 ID
     */
    @TableField("identifier")
    private String identifier;

    /**
     * 可选，存储 AccessToken 或额外信息
     */
    @TableField("credential")
    private String credential;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
