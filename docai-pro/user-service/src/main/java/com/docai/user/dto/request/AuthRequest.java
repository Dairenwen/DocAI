package com.docai.user.dto.request;

import lombok.Data;

/**
 * 统一认证请求参数（注册+登录）
 */
@Data
public class AuthRequest {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String verificationCode;

}
