package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsserver.service.EmailService;
import com.zjj.mkcscommon.enumeration.VerificationCodeType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * 邮件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mkcs.mail.from}")
    private String from;

    @Value("${mkcs.mail.from-name:MK Cloud Storage}")
    private String fromName;

    @Override
    public void sendVerificationCode(String to, String code, String type) {
        try {
            // 获取验证码类型描述
            VerificationCodeType codeType = VerificationCodeType.fromCode(type);
            String typeDesc = codeType.getDescription();

            // 准备模板数据
            Context context = new Context();
            context.setVariable("code", code);
            context.setVariable("type", typeDesc);
            context.setVariable("validMinutes", 5);

            // 渲染 HTML 模板
            String htmlContent = templateEngine.process("verification-code", context);

            // 发送 HTML 邮件
            String subject = String.format("[%s] %s", fromName, typeDesc);
            sendHtmlEmail(to, subject, htmlContent);

            log.info("验证码邮件发送成功: to={}, type={}", to, type);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: to={}, type={}, error={}", to, type, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    @Override
    public void sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("简单邮件发送成功: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("简单邮件发送失败: to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true 表示 HTML 格式

            mailSender.send(message);
            log.info("HTML 邮件发送成功: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("HTML 邮件发送失败: to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
