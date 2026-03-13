package com.docai.user.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 查询用户信息
 */
@Data
@Builder
public class UserInfoResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;
}
