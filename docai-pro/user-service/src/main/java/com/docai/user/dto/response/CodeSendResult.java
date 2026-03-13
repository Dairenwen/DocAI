package com.docai.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码发送结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSendResult {

    /**
     * 验证码过期时间（秒）
     */
    private Integer expireSeconds;

    /**
     * 邮件发送是否成功
     */
    private boolean sendSuccess;

    /**
     * 发送模式：smtp/noop
     */
    private String deliveryMode;
}
