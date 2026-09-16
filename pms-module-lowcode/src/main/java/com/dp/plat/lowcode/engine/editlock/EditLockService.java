package com.dp.plat.lowcode.engine.editlock;

public interface EditLockService {
    /** 获取锁（Redis SETNX + DB 持久化） */
    EditLockInfo acquire(String configType, Long configId, Long userId, String userName);

    /** 心跳续期 */
    EditLockInfo renew(String configType, Long configId, Long userId);

    /** 释放锁 */
    void release(String configType, Long configId, Long userId);

    /** 查询当前持锁人 */
    EditLockInfo getLock(String configType, Long configId);
}
