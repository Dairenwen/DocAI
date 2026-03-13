package com.docai.common.service;

/**
 * 分布式锁服务
 */
public interface DistributeLockService {


    boolean tryLock(String lockKey, String lockValue, long expireTime);



    boolean releaseLock(String lockKey, String lockValue);

}
