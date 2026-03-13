package com.docai.user.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 发送邮箱验证码的标准返回
 */
@Data
@Builder
public class SendCodeResponse {

    /**
     * 脱敏后的邮箱
     */
    private String sendTo;

    /**
     * 验证码的过期时间（秒）
     */
    private Integer expireTime;

}
