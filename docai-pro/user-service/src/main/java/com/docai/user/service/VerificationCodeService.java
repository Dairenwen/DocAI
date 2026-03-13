package com.docai.user.service;

import com.docai.user.dto.response.CodeSendResult;

/**
 * 验证码服务相关接口
 */
public interface VerificationCodeService {

    /**
     * 发送验证码
     * @param email 邮箱
     * @return 过期时长（秒）
     */
    CodeSendResult sendCode(String email);

    /**
     * 校验验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 是否校验通过
     */
    boolean verifyCode(String email, String code);

    /**
     * 根据验证码获取用户的ID
     * @param code 验证码
     * @return 用户ID
     */
    Long getUserId(String code);

    void remove(String code);
}
