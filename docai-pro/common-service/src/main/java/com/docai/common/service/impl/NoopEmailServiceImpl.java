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
        return true;
    }

    @Override
    public boolean sendEmailWithAttachment(String email, String subject, String content,
                                           String attachmentName, byte[] attachmentBytes,
                                           String attachmentType) {
        log.warn("SMTP not configured. Skip sending attachment email to {}.", email);
        return true;
    }
}
