package com.docai.user.service;

import com.docai.user.dto.request.AuthRequest;
import com.docai.user.dto.request.ChangePasswordRequest;
import com.docai.user.dto.response.AuthResponse;
import com.docai.user.dto.response.ChangePasswordResponse;
import com.docai.user.dto.response.UserInfoResponse;

/**
 * 用户服务相关接口
 */
public interface UserService {

    AuthResponse auth(AuthRequest authRequest);

    UserInfoResponse getUserInfo(String authorization);

    ChangePasswordResponse changePassword(ChangePasswordRequest request, String token);

    void logout(String authorization);
}
