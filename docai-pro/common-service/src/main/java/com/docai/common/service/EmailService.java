package com.docai.common.service;

/**
 * 邮件发送服务的接口
 */
public interface EmailService {


    /**
     * 发送邮件验证码
     * @param to 收件人的邮箱
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String to, String code);

    // 发送邮件（文件）到邮箱
    boolean sendEmailWithAttachment(
            String email,
            String subject,
            String content,
            String attachmentName,
            byte[] attachmentBytes,
            String attachmentType
    );

    // 发送纯HTML内容邮件
    boolean sendHtmlEmail(String email, String subject, String htmlContent);
}
