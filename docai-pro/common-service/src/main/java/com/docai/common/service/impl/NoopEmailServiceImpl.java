package com.docai.common.service.impl;

import com.docai.common.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Local fallback email service for environments without SMTP settings.
 */
@Service
@Slf4j
@ConditionalOnExpression("'${spring.mail.username:}' == ''")
public class NoopEmailServiceImpl implements EmailService {

    @Override
    public boolean sendVerificationCode(String to, String code) {
        log.warn("SMTP not configured. Skip sending verification code to {}.", to);
        return true;  // 验证码可以跳过发送，但要记录日志
    }

    @Override
    public boolean sendEmailWithAttachment(String email, String subject, String content,
                                           String attachmentName, byte[] attachmentBytes,
                                           String attachmentType) {
        log.warn("SMTP not configured. Cannot send attachment email to {}.", email);
        throw new RuntimeException("邮件服务未配置（缺少SMTP设置），请联系管理员配置环境变量 DOC_SMTP_USER 和 DOC_SMTP_AUTH_CODE");
    }

    @Override
    public boolean sendHtmlEmail(String email, String subject, String htmlContent) {
        log.warn("SMTP not configured. Cannot send HTML email to {}.", email);
        throw new RuntimeException("邮件服务未配置（缺少SMTP设置），请联系管理员配置环境变量 DOC_SMTP_USER 和 DOC_SMTP_AUTH_CODE");
    }
}
