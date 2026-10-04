package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.dto.SendVerificationCodeRequest;
import cn.zjj.mkcsmodel.dto.VerifyCodeRequest;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 发送验证码（异步通过MQ）
     *
     * @param request 发送请求
     */
    void sendVerificationCode(SendVerificationCodeRequest request);

    /**
     * 验证验证码
     *
     * @param request 验证请求
     * @return 是否验证成功
     */
    boolean verifyCode(VerifyCodeRequest request);

    /**
     * 检查是否可以发送验证码（限流检查）
     *
     * @param email 邮箱
     * @param type  类型
     * @return 是否可以发送
     */
    boolean canSendCode(String email, String type);

    /**
     * 获取剩余冷却时间（秒）
     *
     * @param email 邮箱
     * @param type  类型
     * @return 剩余秒数，0表示可以发送
     */
    long getRemainingCooldown(String email, String type);
}
