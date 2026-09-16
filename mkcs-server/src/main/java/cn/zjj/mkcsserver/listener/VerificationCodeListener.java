package cn.zjj.mkcsserver.listener;

import cn.zjj.mkcsmodel.dto.VerificationCodeMessage;
import cn.zjj.mkcsserver.config.RabbitMQConfiguration;
import cn.zjj.mkcsserver.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 验证码消息监听器
 * @author 34978
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeListener {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private static final String MESSAGE_ID_PREFIX = "verification:msg:";

    @RabbitListener(queues = RabbitMQConfiguration.VERIFICATION_CODE_QUEUE)
    public void handleVerificationCodeMessage(VerificationCodeMessage message) {
        String messageId = message.getMessageId();
        String email = message.getEmail();
        String code = message.getCode();
        String type = message.getType();

        log.info("收到验证码消息: email={}, type={}, messageId={}", email, type, messageId);

        // 幂等性检查
        String messageIdKey = MESSAGE_ID_PREFIX + messageId;
        Boolean exists = redisTemplate.hasKey(messageIdKey);
        if (!exists) {
            log.warn("消息已处理过，跳过: messageId={}", messageId);
            return;
        }

        try {
            // 发送邮件（使用 HTML 模板）
            emailService.sendVerificationCode(email, code, type);

            // 标记消息已处理（删除幂等键）
            redisTemplate.delete(messageIdKey);

            log.info("验证码发送成功: email={}, type={}", email, type);
        } catch (Exception e) {
            log.error("验证码发送失败: email={}, type={}, error={}", email, type, e.getMessage(), e);
            // 清除Redis key，允许消息重新入队时再次尝试处理
            redisTemplate.delete(messageIdKey);
            throw e;
        }
    }
}
