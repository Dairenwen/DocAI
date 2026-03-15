package com.docai.common.service.impl;

import com.docai.common.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务的实现类
 */
@Service
@Slf4j
public class RedisServiceImpl implements RedisService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证码前缀
     */
    private static final String CODE_PREFIX = "code:";

    /**
     * 个人令牌前缀
     */
    private static final String TOKEN_PREFIX = "token:";

    /**
     * 老用户令牌
     */
    private static final String USER_SESSION_PREFIX = "user_session:";

    @Override
    public void storeVerificationCode(String code, Long userId, String userName, String loginKey) {
        // 1. 先生成key
        String key = CODE_PREFIX + code;
        CodeInfo codeInfo = new CodeInfo(userId, userName, loginKey);
        // 2. 对象序列化之后，写入redis
        try {
            String value = objectMapper.writeValueAsString(codeInfo);
            stringRedisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
            log.info("[Redis存储成功] 验证码 {}  用户名 {}", code, userName);
        } catch (JsonProcessingException e) {
            log.error("[Redis存储失败] 验证码 {}  用户名 {}", code, userName);
        }
    }

    @Override
    public Long getUserIdByCode(String code) {
        // 1. 先生成key
        String key = CODE_PREFIX + code;
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value != null) {
            try {
                CodeInfo codeInfo = objectMapper.readValue(value, CodeInfo.class);
                return codeInfo.userId;
            } catch (JsonProcessingException e) {
                log.error("[Redis读取失败] 验证码 {}  报错 {}", code, e.getMessage());
            }
        }
        return null;
    }

    @Override
    public boolean isCodeValid(String code) {
        // 1. 先生成key
        String key = CODE_PREFIX + code;
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public boolean isCodeValid(String code, String loginKey) {
        String key = CODE_PREFIX + code;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            CodeInfo codeInfo = objectMapper.readValue(value, CodeInfo.class);
            if (codeInfo == null) {
                return false;
            }
            if (loginKey == null || loginKey.isBlank()) {
                return false;
            }
            return loginKey.equalsIgnoreCase(codeInfo.loginKey);
        } catch (JsonProcessingException e) {
            log.error("[Redis读取失败] 验证码 {}  报错 {}", code, e.getMessage());
            return false;
        }
    }

    @Override
    public void remove(String code) {
        String key = CODE_PREFIX + code;
        stringRedisTemplate.delete(key);
        log.info("验证码已经删除{}", code);
    }

    @Override
    public void storeToken(String token, Long userId, String username, long expireSeconds) {
        String key = TOKEN_PREFIX + token;
        TokenInfo tokenInfo = new TokenInfo(userId, username);
        try {
            String value = objectMapper.writeValueAsString(tokenInfo);
            stringRedisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("[Redis令牌存储失败 {} {}]", token, e.getMessage());
        }
    }

    @Override
    public void storeUserActiveToken(Long userId, String token, long expireSeconds) {
        String key = USER_SESSION_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, token, expireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getUserToken(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean isUserLogged(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public Long getUserIdByAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        // 1. 获取原生的token
        String token = authorization.replaceFirst("(?i)^Bearer ", "").trim();
        String key = TOKEN_PREFIX + token;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        try {
            TokenInfo tokenInfo = objectMapper.readValue(value, TokenInfo.class);
            return tokenInfo.userId;
        } catch (JsonProcessingException e) {
            log.error("redis读取失败 {} {}", token, e.getMessage());
        }

        return null;
    }

    @Override
    public void removeAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return;
        }
        // 1. 获取原生的token
        String token = authorization.replaceFirst("(?i)^Bearer ", "").trim();
        // 2. 删除用户令牌缓存
        String tokenKey = TOKEN_PREFIX + token;
        Long userId = getUserIdByAuthorization(token);
        stringRedisTemplate.delete(tokenKey);
        // 3. 删除活跃用户令牌缓存
        String userKey = USER_SESSION_PREFIX + userId;
        stringRedisTemplate.delete(userKey);
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CodeInfo{

        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名：邮箱或者用户名
         */
        private String userName;

        /**
         * 登录标识（邮箱/用户名），用于验证码归属校验
         */
        private String loginKey;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class TokenInfo{

        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名：邮箱或者用户名
         */
        private String userName;
    }

}
