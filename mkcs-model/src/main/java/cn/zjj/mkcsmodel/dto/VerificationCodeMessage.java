package cn.zjj.mkcsmodel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 验证码消息（MQ消息体）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCodeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 验证码
     */
    private String code;

    /**
     * 验证码类型
     */
    private String type;

    /**
     * 消息ID（用于幂等）
     */
    private String messageId;
}
