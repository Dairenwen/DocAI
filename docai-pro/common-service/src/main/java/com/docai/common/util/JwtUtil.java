package com.docai.common.util;

import com.docai.common.service.RedisService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类：复杂生成、校验、管理用户登录令牌
 */
@Component
public class JwtUtil {

    @Autowired
    private RedisService redisService;

    @Value("72000")
    private Long expireTime;

    /**
     * 签名秘钥对象
     */
    private SecretKey key;

    /**
     * H256秘钥
     */
    @Value("changeit-change-it-change-it-change-it-change-it")
    private String secret;

    /**
     * 初始化秘钥对象
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成JWT令牌并写入缓存
     * @param userId 用户ID
     * @param username 用户名或邮箱
     * @return 令牌
     */
    public String createToken(Long userId, String username) {
        Date now = new Date();

        Date expiry = new Date(now.getTime() + expireTime);

        String token = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        // token存到redis
        redisService.storeToken(token, userId, username, expireTime);

        // 存储活跃用户（避免重复登录）
        redisService.storeUserActiveToken(userId, token, expireTime);

        return token;
    }

    /**
     * 获取当前用户的token
     * @param userId 用户ID
     * @return 用户token
     */
    public String getUserActiveToken(Long userId) {
        return redisService.getUserToken(userId);
    }

    /**
     * 检查用户是否已经登录
     * @param userId 用户ID
     * @return 是否登录
     */
    public boolean isUserLogged(Long userId) {
        return redisService.isUserLogged(userId);
    }

    /**
     * 获取用户ID
     * @param authorization 前端传递的令牌
     * @return 用户ID
     */
    public Long getUserIdByAuthorization(String authorization) {
        // 1. 从redis中去获取
        return redisService.getUserIdByAuthorization(authorization);
    }

    /**
     * 删除令牌
     * @param authorization 用户令牌
     */
    public void removeAuthorization(String authorization) {
        redisService.removeAuthorization(authorization);
    }
}
