package com.docai.user.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.docai.common.util.JwtUtil;
import com.docai.user.dto.request.AuthRequest;
import com.docai.user.dto.request.ChangePasswordRequest;
import com.docai.user.dto.request.ResetPasswordByEmailRequest;
import com.docai.user.dto.response.AuthResponse;
import com.docai.user.dto.response.ChangePasswordResponse;
import com.docai.user.dto.response.UserInfoResponse;
import com.docai.user.entity.UserEntity;
import com.docai.user.mapper.UserMapper;
import com.docai.user.service.UserService;
import com.docai.user.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse auth(AuthRequest authRequest) {
        // 1. 先检测当前是哪种方式（邮箱+验证码/账号+密码）
        boolean isEmailCodeMode = StringUtils.isNotBlank(authRequest.getEmail()) && StringUtils.isNotBlank(authRequest.getVerificationCode());
        boolean isPasswordMode = StringUtils.isNotBlank(authRequest.getUsername()) && StringUtils.isNotBlank(authRequest.getPassword());

        if (!isEmailCodeMode && !isPasswordMode) {
            throw new IllegalArgumentException("请提供有效的验证方式：邮箱+验证码/账号+密码");
        }

        UserEntity user = null;
        Boolean isNewUser = false;

        // 2. 先处理邮箱+验证码的方式
        if (isEmailCodeMode) {
            if (!verificationCodeService.verifyCode(authRequest.getEmail(), authRequest.getVerificationCode())) {
                throw new IllegalArgumentException("验证码无效或者过期");
            }
            // 3. 注册还是登录
            Long userId = verificationCodeService.getUserId(authRequest.getVerificationCode());

            // 4. 新用户来注册
            if (userId == 0L) {
                user = new UserEntity();
                user.setEmail(authRequest.getEmail());
                if (StringUtils.isNotBlank(authRequest.getUsername()) && userMapper.existByUserName(authRequest.getUsername()) == 0) {
                    user.setUserName(authRequest.getUsername());
                } else {
                    user.setUserName(createRandomName());
                }
                userMapper.insert(user);
                isNewUser = true;
            } else {
                // 老用户登录，直接查数据库完善user信息
                user = userMapper.selectById(userId);
            }

            // 5. 删除验证码，避免重复使用
            verificationCodeService.remove(authRequest.getVerificationCode());
        }

        if (isPasswordMode) {
            user = userMapper.findByLoginKey(authRequest.getUsername());

            if (user == null) {
                // 新用户，需要先注册
                user = new UserEntity();
                user.setUserName(authRequest.getUsername());
                user.setPasswordHash(passwordEncoder.encode(authRequest.getPassword()));
                userMapper.insert(user);
                isNewUser = true;
            } else {
                // 老用户，校验密码
                if (!passwordEncoder.matches(authRequest.getPassword(), user.getPasswordHash())) {
                    throw new IllegalArgumentException("用户名与密码不匹配");
                }
            }
        }

        // 处理token
        String token;

        // 重复登录直接获取
        if (jwtUtil.isUserLogged(user.getId())) {
            token = jwtUtil.getUserActiveToken(user.getId());
        } else {
            token = jwtUtil.createToken(user.getId(), user.getUserName());
        }

        LocalDateTime time = LocalDateTime.now();

        LocalDateTime expireTime = time.plusHours(20);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
        String tokenExpireTime = expireTime.format(formatter);

        return  AuthResponse.builder()
                .userId(user.getId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .token(token)
                .tokenExpireTime(tokenExpireTime)
                .isNewUser(isNewUser)
                .build();
    }

    @Override
    public UserInfoResponse getUserInfo(String authorization) {
        // 1. 去JWT工具类里面查询用户ID
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            throw new IllegalArgumentException("无效的令牌");
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }



        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .email(maskEmail(user.getEmail()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangePasswordResponse changePassword(ChangePasswordRequest request, String token) {
        // 1. 新密码与确认密码必须一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("请保证新旧密码一致");
        }

        // 2. 新密码与老密码不能相同
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new IllegalArgumentException("新旧密码不能相同");
        }

        // 3. 根据token获取用户信息
        Long userId = jwtUtil.getUserIdByAuthorization(token);
        if (userId == null) {
            throw new IllegalArgumentException("令牌无效");
        }

        // 4. 获取到用户信息之后，先验证原密码
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("当前账户通过邮箱验证码注册，未设置密码，无法通过此方式修改密码");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);


        // 5. 封装响应
        return ChangePasswordResponse.builder()
                .userId(userId)
                .username(user.getUserName())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordByEmail(ResetPasswordByEmailRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("请保证新旧密码一致");
        }

        if (!verificationCodeService.verifyCode(request.getEmail(), request.getVerificationCode())) {
            throw new IllegalArgumentException("验证码无效或者过期");
        }

        UserEntity user = userMapper.findByLoginKey(request.getEmail());
        if (user == null || StringUtils.isBlank(user.getEmail())) {
            throw new IllegalArgumentException("该邮箱尚未注册");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        verificationCodeService.remove(request.getVerificationCode());
    }

    @Override
    public void logout(String authorization) {
        jwtUtil.removeAuthorization(authorization);
    }


    /**
     * 用来生成随机用户名
     * @return 用户名
     */
    private String createRandomName() {
        String name = "docai_" + String.valueOf((int) (Math.random() * 900000000) + 100000000);
        // 1. 用户名需要全局唯一
        if (userMapper.existByUserName(name) == 0) {
            return name;
        }
        return "docai_" + System.currentTimeMillis();
    }

    private String maskEmail(String email) {
        if (StringUtils.isNotBlank(email)) {
            String[] parts = email.split("@", 2);
            String name = parts[0];
            String domain = parts[1];
            if (name.length() <= 2) {
                return name.charAt(0) + "***@" +domain;
            }
            return name.substring(0, 2) + "***@" +domain;
        }
        return email;
    }
}
