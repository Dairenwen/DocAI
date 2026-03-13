package com.docai.ai.function;

import com.docai.common.service.EmailService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;

/**
 * 邮件发送工具类，让LLm来调用
 */
@Service
@Slf4j
public class EmailSendTool implements Function<EmailSendTool.Request, EmailSendTool.Response> {

    @Autowired
    private EmailService emailService;

    // 声明发送邮件的工具方法
    @Tool
    public Response sendEmail(
            String email,
            String subject,
            String content,
            String attachmentUrl,
            String attachmentName
    ) {
        return apply(new Request(email, subject, content, attachmentUrl, attachmentName));
    }


    // 执行发送邮件的操作
    @Override
    public EmailSendTool.Response apply(EmailSendTool.Request request) {

        // 1. 从url地址读取字节数组
        byte[] attachmentBytes = null;
        try {
            URL url = new URL(request.attachmentUrl);
            URLConnection connection = url.openConnection();

            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int byteRead;
            while ((byteRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, byteRead);
            }
            attachmentBytes = outputStream.toByteArray();
        } catch (Exception e) {
            return new Response(false, "下载附加失败" + e.getMessage());
        }

        // 2. 调用邮件服务，发送邮件
        boolean success = emailService.sendEmailWithAttachment(
                request.getEmail(),
                request.getSubject(),
                request.getContent(),
                request.getAttachmentName(),
                attachmentBytes,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        if (success) {
            log.info("邮件发送成功，收件人{}", request.getEmail());
            return new Response(true, "邮件发送成功");
        }
        log.info("邮件发送失败，收件人{}", request.getEmail());
        return new Response(false, "邮件发送失败");
    }




    // 邮件发送请求类
    @Data
    @AllArgsConstructor
    public class Request {
        private String email;  // 收件人的邮箱
        private String subject; //邮件主题
        private String content; //正文内容
        private String attachmentUrl; //附件地址
        private String attachmentName; //附件名称
    }

    // 邮件发送响应类
    @Data
    @AllArgsConstructor
    public class Response {
        private boolean success; // 邮件是否发送成功
        private String message; //提示文案
    }
}
