package com.docai.user.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.docai.common.service.EmailService;
import com.docai.common.service.RedisService;
import com.docai.user.dto.response.CodeSendResult;
import com.docai.user.entity.UserEntity;
import com.docai.user.mapper.UserMapper;
import com.docai.user.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 验证码校验服务实现类
 */
@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired(required = false)
    private EmailService emailService;


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisService redisService;

    @Value("${spring.mail.username:}")
    private String mailUserName;

    @Override
    public CodeSendResult sendCode(String email) {
        // 1. 生成验证码
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        // 2. 发送验证码（本地未配置邮件时允许跳过）
        boolean ifSendSuccess = true;
        String deliveryMode = StringUtils.isNotBlank(mailUserName) ? "smtp" : "noop";
        if (emailService != null) {
            ifSendSuccess = emailService.sendVerificationCode(email, code);
        } else {
            log.warn("EmailService not configured, skip sending verification code email.");
        }

        // 3. 查询数据库的判断逻辑 (根据邮箱去查询users表，存在即为登录，不存在即为注册)
        UserEntity user = userMapper.findByLoginKey(email);

        // 新注册
        if (user == null) {
            user = new UserEntity();
            user.setId(0L);
            user.setUserName(email);
        }

        // 验证码信息写到redis缓存
        redisService.storeVerificationCode(code, user.getId(), user.getUserName(), email);

        if (ifSendSuccess) {
            log.info("[验证码发送成功], 验证码{} 已经发送至{}", code, email);
            return CodeSendResult.builder()
                    .expireSeconds(300)
                    .sendSuccess(true)
                    .deliveryMode(deliveryMode)
                    .build();
        } else {
            log.error("[验证码发送失败], 验证码{} 没有发送至{}", code, email);
            return CodeSendResult.builder()
                    .expireSeconds(-1)
                    .sendSuccess(false)
                    .deliveryMode(deliveryMode)
                    .build();
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        return redisService.isCodeValid(code, email);
    }

    @Override
    public Long getUserId(String code) {
        return redisService.getUserIdByCode(code);
    }

    @Override
    public void remove(String code) {
        redisService.remove(code);
    }
}
