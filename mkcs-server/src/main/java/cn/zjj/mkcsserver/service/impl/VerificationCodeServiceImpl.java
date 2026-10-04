package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.dto.SendVerificationCodeRequest;
import cn.zjj.mkcsmodel.dto.VerificationCodeMessage;
import cn.zjj.mkcsmodel.dto.VerifyCodeRequest;
import cn.zjj.mkcsserver.config.RabbitMQConfiguration;
import cn.zjj.mkcsserver.service.VerificationCodeService;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.enumeration.VerificationCodeType;
import com.zjj.mkcscommon.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final CryptoUtil cryptoUtil;

    // Redis Key 前缀
    private static final String CODE_PREFIX = "verification:code:";
    private static final String COOLDOWN_PREFIX = "verification:cooldown:";
    private static final String FAIL_COUNT_PREFIX = "verification:fail:";
    private static final String MESSAGE_ID_PREFIX = "verification:msg:";

    // 配置常量
    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL = 5; // 验证码有效期5分钟
    private static final long COOLDOWN_SECONDS = 60; // 发送冷却时间60秒
    private static final int MAX_FAIL_COUNT = 5; // 最大失败次数
    private static final long FAIL_COUNT_TTL = 30; // 失败次数记录30分钟

    @Override
    public void sendVerificationCode(SendVerificationCodeRequest request) {
        String email = request.getEmail();
        String type = request.getType();

        // 验证类型
        VerificationCodeType.fromCode(type);

        // 检查冷却时间
        Assert.isTrue(canSendCode(email, type), ResultCode.OPERATION_FAILED, 
                "发送过于频繁，请" + getRemainingCooldown(email, type) + "秒后再试");

        // 生成验证码
        String code = generateCode();
        log.info("生成验证码: email={}, type={}, code={}", email, type, code);

        // 使用 CryptoUtil 加盐哈希存储到 Redis
        String hashedCode = cryptoUtil.hashPassword(code);
        String codeKey = buildCodeKey(email, type);
        redisTemplate.opsForValue().set(codeKey, hashedCode, CODE_TTL, TimeUnit.MINUTES);

        // 设置冷却时间
        String cooldownKey = buildCooldownKey(email, type);
        redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // 生成消息ID（幂等）
        String messageId = UUID.randomUUID().toString();
        String messageIdKey = MESSAGE_ID_PREFIX + messageId;
        redisTemplate.opsForValue().set(messageIdKey, "1", 10, TimeUnit.MINUTES);

        // 发送到 MQ（异步解耦）
        VerificationCodeMessage message = new VerificationCodeMessage(email, code, type, messageId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.VERIFICATION_CODE_EXCHANGE,
                RabbitMQConfiguration.VERIFICATION_CODE_ROUTING_KEY,
                message
        );

        log.info("验证码消息已发送到MQ: email={}, type={}, messageId={}", email, type, messageId);
    }

    @Override
    public boolean verifyCode(VerifyCodeRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String type = request.getType();

        // 验证类型
        VerificationCodeType.fromCode(type);

        // 检查失败次数
        String failCountKey = buildFailCountKey(email, type);
        String failCountStr = redisTemplate.opsForValue().get(failCountKey);
        int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);

        Assert.isTrue(failCount < MAX_FAIL_COUNT, ResultCode.OPERATION_FAILED,
                "验证失败次数过多，请重新获取验证码");

        // 从 Redis 获取哈希后的验证码
        String codeKey = buildCodeKey(email, type);
        String hashedCode = redisTemplate.opsForValue().get(codeKey);

        if (hashedCode == null) {
            log.warn("验证码不存在或已过期: email={}, type={}", email, type);
            incrementFailCount(email, type);
            return false;
        }

        // 使用 CryptoUtil 验证码匹配
        boolean matches = cryptoUtil.verifyPassword(code, hashedCode);

        if (matches) {
            // 验证成功，删除验证码和失败计数
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failCountKey);
            log.info("验证码验证成功: email={}, type={}", email, type);
            return true;
        } else {
            // 验证失败，增加失败次数
            incrementFailCount(email, type);
            log.warn("验证码验证失败: email={}, type={}, failCount={}", email, type, failCount + 1);
            return false;
        }
    }

    @Override
    public boolean canSendCode(String email, String type) {
        String cooldownKey = buildCooldownKey(email, type);
        return !redisTemplate.hasKey(cooldownKey);
    }

    @Override
    public long getRemainingCooldown(String email, String type) {
        String cooldownKey = buildCooldownKey(email, type);
        Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * 生成随机验证码
     */
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 增加失败次数
     */
    private void incrementFailCount(String email, String type) {
        String failCountKey = buildFailCountKey(email, type);
        Long count = redisTemplate.opsForValue().increment(failCountKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failCountKey, FAIL_COUNT_TTL, TimeUnit.MINUTES);
        }
    }

    /**
     * 构建验证码 Redis Key
     */
    private String buildCodeKey(String email, String type) {
        return CODE_PREFIX + type + ":" + email;
    }

    /**
     * 构建冷却时间 Redis Key
     */
    private String buildCooldownKey(String email, String type) {
        return COOLDOWN_PREFIX + type + ":" + email;
    }

    /**
     * 构建失败次数 Redis Key
     */
    private String buildFailCountKey(String email, String type) {
        return FAIL_COUNT_PREFIX + type + ":" + email;
    }
}
