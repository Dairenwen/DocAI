package com.docai.user.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 修改密码的响应
 */
@Data
@Builder
public class ChangePasswordResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 是否成功
     */
    @Builder.Default
    private boolean success = true;
}
