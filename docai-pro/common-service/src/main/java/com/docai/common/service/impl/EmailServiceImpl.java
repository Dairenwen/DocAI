package com.docai.common.service.impl;

import com.docai.common.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 发送邮件的实现类
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.mail.username")
public class EmailServiceImpl implements EmailService {

    /**
     * Spring 邮件发送器
     */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * 发送方邮箱的地址
     */
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 构建发送邮件验证码的正文
     * @param code 验证码
     * @return 正文
     */
    private String getContent(String code) {
        return String.format("您好！"+
                "您的验证码是: %s\n\n"+
                "验证码的有效期是5分钟，请及时使用",
                code
                );
    }

    @Override
    public boolean sendVerificationCode(String to, String code) {

        // 发送邮件的逻辑

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);

        String subject = "系统验证码";
        String content = getContent(code);

        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
        log.info("[发送邮件] 验证码 {}  已发送至 {}", code, to);
        return true;
    }

    @Override
    public boolean sendEmailWithAttachment(String email, String subject, String content, String attachmentName, byte[] attachmentBytes, String attachmentType) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(content, true);

            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes), attachmentType);
            mailSender.send(message);
            log.info("邮件已经发送至:{}，主题:{}, 附件:{}", email, subject, attachmentName);
            return true;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean sendHtmlEmail(String email, String subject, String htmlContent) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("HTML邮件已发送至:{}, 主题:{}", email, subject);
            return true;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
