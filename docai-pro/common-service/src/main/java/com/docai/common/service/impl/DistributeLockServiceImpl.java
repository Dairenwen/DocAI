package com.docai.common.service.impl;

import com.docai.common.service.DistributeLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁的实现类
 */
@Service
@Slf4j
public class DistributeLockServiceImpl implements DistributeLockService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock_prefix:";


    private final DefaultRedisScript<Long> redisScript;

    private static final String SCRIPT_TEXT=
            "if redis.call('get', KEYSp[1] == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";


    public DistributeLockServiceImpl() {
        redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(SCRIPT_TEXT);
        redisScript.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(String lockKey, String lockValue, long expireTime) {
        // 1. 补全完整的key
        String fullLockKey = LOCK_PREFIX + lockKey;

        // 2. 把value写入redis
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                fullLockKey,
                lockValue,
                expireTime,
                TimeUnit.SECONDS
        );

        if (result) {
            log.info("成功获取分布式锁{}:{}", fullLockKey, lockValue);
        } else {
            log.error("获取分布式锁{}失败, 被别的请求站上了", lockKey);
        }

        return result;
    }

    @Override
    public boolean releaseLock(String lockKey, String lockValue) {
        // 1. 补全完整的key
        String fullLockKey = LOCK_PREFIX + lockKey;

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(fullLockKey),
                lockValue
        );

        boolean released = result != null && result > 0;
        if (released) {
            log.info("成功释放分布式锁{}", lockKey);
        } else {
            log.error("释放分布式锁失败{}", lockKey);
        }

        return false;
    }
}
