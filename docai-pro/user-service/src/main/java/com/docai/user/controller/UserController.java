package com.docai.user.controller;

import com.docai.common.annotation.LogOperation;
import com.docai.common.util.Result;
import com.docai.user.dto.request.AuthRequest;
import com.docai.user.dto.request.ChangePasswordRequest;
import com.docai.user.dto.request.SendCodeRequest;
import com.docai.user.dto.response.AuthResponse;
import com.docai.user.dto.response.ChangePasswordResponse;
import com.docai.user.dto.response.SendCodeResponse;
import com.docai.user.dto.response.UserInfoResponse;
import com.docai.user.service.UserService;
import com.docai.user.service.VerificationCodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户服务的控制器
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserService userService;


    /**
     * 发送邮箱验证码
     */
    @PostMapping("/verification-code")
    @LogOperation("发送验证码")
    public Result<SendCodeResponse> sendVerificationCode(@RequestBody @Valid SendCodeRequest request) {
        int expireSeconds = verificationCodeService.sendCode(request.getEmail());
        String sendTo = maskEmail(request.getEmail());
        SendCodeResponse response = SendCodeResponse.builder()
                .expireTime(expireSeconds)
                .sendTo(sendTo)
                .build();
        return Result.success("验证码发送成功", response);
    }


    @PostMapping("/auth")
    @LogOperation("统一认证注册与登录")
    public Result<AuthResponse> auth(@RequestBody @Valid AuthRequest authRequest) {
        AuthResponse authResponse = userService.auth(authRequest);
        String message = authResponse.getIsNewUser() ? "注册并登录成功" : "登录成功";
        return Result.success(message, authResponse);
    }


    /**
     * 用户信息查询
     */
    @GetMapping("/info")
    @LogOperation("用户信息查询")
    public Result<UserInfoResponse> getUserInfo(@RequestHeader(value = "Authorization", required = true) String authorization) {
        return Result.success("success", userService.getUserInfo(authorization));
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    @LogOperation("修改密码")
    public Result<ChangePasswordResponse> changePassword(
            @RequestHeader(value = "Authorization", required = true) String authorization,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        return Result.success("success", userService.changePassword(request, authorization));
    }

    /**
     * 用户退出系统
     */
    @PostMapping("/logout")
    @LogOperation("用户退出")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = true) String authorization) {
        userService.logout(authorization);
        return Result.success("登出成功", null);
    }

    /**
     * 邮箱脱敏
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    private String maskEmail(String email) {
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" +domain;
        }
        return name.substring(0, 2) + "***@" +domain;
    }
}
