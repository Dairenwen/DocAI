package com.docai.user.service.impl;

import com.docai.common.util.JwtUtil;
import com.docai.user.dto.request.AuthRequest;
import com.docai.user.dto.request.ChangePasswordRequest;
import com.docai.user.dto.response.AuthResponse;
import com.docai.user.dto.response.ChangePasswordResponse;
import com.docai.user.dto.response.UserInfoResponse;
import com.docai.user.entity.UserEntity;
import com.docai.user.mapper.UserMapper;
import com.docai.user.service.VerificationCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private VerificationCodeService verificationCodeService;

    @Nested
    @DisplayName("auth - 用户认证（注册+登录）")
    class AuthTest {
        @Test
        void shouldRegisterNewUserWithPassword() {
            AuthRequest request = new AuthRequest();
            request.setUsername("newuser");
            request.setPassword("Password123");

            when(userMapper.findByLoginKey("newuser")).thenReturn(null);
            when(jwtUtil.isUserLogged(any())).thenReturn(false);
            when(jwtUtil.createToken(any(), eq("newuser"))).thenReturn("test-token");

            AuthResponse response = userService.auth(request);

            assertTrue(response.getIsNewUser());
            assertEquals("newuser", response.getUserName());
            assertEquals("test-token", response.getToken());
            verify(userMapper).insert((UserEntity) argThat(u -> u instanceof UserEntity));
        }

        @Test
        void shouldLoginExistingUserWithCorrectPassword() {
            AuthRequest request = new AuthRequest();
            request.setUsername("existing");
            request.setPassword("CorrectPass123");

            UserEntity existingUser = new UserEntity();
            existingUser.setId(100L);
            existingUser.setUserName("existing");
            existingUser.setPasswordHash(new BCryptPasswordEncoder().encode("CorrectPass123"));

            when(userMapper.findByLoginKey("existing")).thenReturn(existingUser);
            when(jwtUtil.isUserLogged(100L)).thenReturn(false);
            when(jwtUtil.createToken(100L, "existing")).thenReturn("login-token");

            AuthResponse response = userService.auth(request);

            assertFalse(response.getIsNewUser());
            assertEquals(100L, response.getUserId());
            assertEquals("login-token", response.getToken());
            verify(userMapper, never()).insert((UserEntity) argThat(u -> u instanceof UserEntity));
        }

        @Test
        void shouldRejectWrongPassword() {
            AuthRequest request = new AuthRequest();
            request.setUsername("existing");
            request.setPassword("WrongPass");

            UserEntity existingUser = new UserEntity();
            existingUser.setId(100L);
            existingUser.setUserName("existing");
            existingUser.setPasswordHash(new BCryptPasswordEncoder().encode("CorrectPass"));

            when(userMapper.findByLoginKey("existing")).thenReturn(existingUser);

            assertThrows(IllegalArgumentException.class, () -> userService.auth(request));
        }

        @Test
        void shouldRejectEmptyCredentials() {
            AuthRequest request = new AuthRequest();
            // No username, password, email, or verification code

            assertThrows(IllegalArgumentException.class, () -> userService.auth(request));
        }

        @Test
        void shouldReuseExistingToken() {
            AuthRequest request = new AuthRequest();
            request.setUsername("existing");
            request.setPassword("Pass123");

            UserEntity user = new UserEntity();
            user.setId(100L);
            user.setUserName("existing");
            user.setPasswordHash(new BCryptPasswordEncoder().encode("Pass123"));

            when(userMapper.findByLoginKey("existing")).thenReturn(user);
            when(jwtUtil.isUserLogged(100L)).thenReturn(true);
            when(jwtUtil.getUserActiveToken(100L)).thenReturn("existing-token");

            AuthResponse response = userService.auth(request);

            assertEquals("existing-token", response.getToken());
            verify(jwtUtil, never()).createToken(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("getUserInfo - 获取用户信息")
    class GetUserInfoTest {
        @Test
        void shouldReturnUserInfo() {
            when(jwtUtil.getUserIdByAuthorization("Bearer token")).thenReturn(100L);
            UserEntity user = new UserEntity();
            user.setId(100L);
            user.setUserName("testuser");
            user.setEmail("test@example.com");
            when(userMapper.selectById(100L)).thenReturn(user);

            UserInfoResponse response = userService.getUserInfo("Bearer token");

            assertEquals(100L, response.getUserId());
            assertEquals("testuser", response.getUsername());
            assertEquals("te***@example.com", response.getEmail());
        }

        @Test
        void shouldRejectInvalidToken() {
            when(jwtUtil.getUserIdByAuthorization("invalid")).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () -> userService.getUserInfo("invalid"));
        }

        @Test
        void shouldRejectNonExistentUser() {
            when(jwtUtil.getUserIdByAuthorization("Bearer token")).thenReturn(999L);
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () -> userService.getUserInfo("Bearer token"));
        }
    }

    @Nested
    @DisplayName("changePassword - 修改密码")
    class ChangePasswordTest {
        @Test
        void shouldRejectMismatchedPasswords() {
            ChangePasswordRequest req = ChangePasswordRequest.builder()
                    .currentPassword("old123")
                    .newPassword("new123")
                    .confirmPassword("different123")
                    .build();

            assertThrows(IllegalArgumentException.class, () -> userService.changePassword(req, "token"));
        }

        @Test
        void shouldRejectSamePassword() {
            ChangePasswordRequest req = ChangePasswordRequest.builder()
                    .currentPassword("same123")
                    .newPassword("same123")
                    .confirmPassword("same123")
                    .build();

            assertThrows(IllegalArgumentException.class, () -> userService.changePassword(req, "token"));
        }
    }

    @Nested
    @DisplayName("maskEmail - 邮箱脱敏")
    class MaskEmailTest {
        private String callMaskEmail(String email) throws Exception {
            Method method = UserServiceImpl.class.getDeclaredMethod("maskEmail", String.class);
            method.setAccessible(true);
            return (String) method.invoke(userService, email);
        }

        @Test
        void shouldMaskNormalEmail() throws Exception {
            assertEquals("te***@example.com", callMaskEmail("test@example.com"));
        }

        @Test
        void shouldMaskLongPrefix() throws Exception {
            assertEquals("lo***@qq.com", callMaskEmail("longprefix@qq.com"));
        }

        @Test
        void shouldMaskShortPrefix() throws Exception {
            assertEquals("a***@test.com", callMaskEmail("ab@test.com"));
        }

        @Test
        void shouldHandleNull() throws Exception {
            assertNull(callMaskEmail(null));
        }

        @Test
        void shouldHandleBlank() throws Exception {
            assertEquals("", callMaskEmail(""));
        }
    }
}
