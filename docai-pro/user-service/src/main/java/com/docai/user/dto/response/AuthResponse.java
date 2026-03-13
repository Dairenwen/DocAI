package com.docai.user.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 统一认证响应参数(注册+登录)
 */
@Data
@Builder
public class AuthResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户访问令牌
     */
    private String token;

    /**
     * 令牌的过期时间
     */
    private String tokenExpireTime;

    /**
     * 是否为新用户
     */
    private Boolean isNewUser;

}
