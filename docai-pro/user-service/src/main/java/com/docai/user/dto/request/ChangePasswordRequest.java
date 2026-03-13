package com.docai.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * 修改密码的请求参数
 */
@Data
@Builder
public class ChangePasswordRequest {

    /**
     * 当前的密码
     */
    @NotBlank(message = "当前密码必填")
    private String currentPassword;


    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度介于6到64个字符之间")
    private String newPassword;

    /**
     * 确认密码必填
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

}
