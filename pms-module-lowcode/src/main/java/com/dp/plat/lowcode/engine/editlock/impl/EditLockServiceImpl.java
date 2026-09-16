package com.dp.plat.lowcode.engine.editlock.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.engine.editlock.EditLockInfo;
import com.dp.plat.lowcode.engine.editlock.EditLockService;
import com.dp.plat.lowcode.entity.LowCodeEditLock;
import com.dp.plat.lowcode.mapper.LowCodeEditLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditLockServiceImpl implements EditLockService {

    private static final String LOCK_KEY_PREFIX = "lowcode:editlock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration RENEW_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final LowCodeEditLockMapper editLockMapper;

    @Override
    public EditLockInfo acquire(String configType, Long configId, Long userId, String userName) {
        String key = buildKey(configType, configId);
        String value = String.valueOf(userId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, value, LOCK_TTL.toSeconds(), TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(acquired)) {
            // 删除旧持久化记录，写入新记录
            editLockMapper.delete(new LambdaQueryWrapper<LowCodeEditLock>()
                    .eq(LowCodeEditLock::getConfigType, configType)
                    .eq(LowCodeEditLock::getConfigId, configId));
            LowCodeEditLock record = LowCodeEditLock.builder()
                    .configType(configType).configId(configId).userId(userId).userName(userName)
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plus(LOCK_TTL))
                    .renewCount(0)
                    .build();
            editLockMapper.insert(record);
            log.info("编辑锁获取成功: {}/{} by user {}", configType, configId, userId);
            return EditLockInfo.builder()
                    .configType(configType).configId(configId).userId(userId).userName(userName)
                    .acquiredAt(record.getAcquiredAt()).expireAt(record.getExpireAt())
                    .acquired(true).message("获取成功").build();
        }
        // 已被其他人持有
        EditLockInfo existing = getLock(configType, configId);
        return EditLockInfo.builder()
                .configType(configType).configId(configId)
                .acquired(false)
                .message("配置正被 " + (existing != null ? existing.getUserName() : "其他人") + " 编辑中")
                .build();
    }

    @Override
    public EditLockInfo renew(String configType, Long configId, Long userId) {
        String key = buildKey(configType, configId);
        String currentValue = redisTemplate.opsForValue().get(key);
        if (currentValue == null || !currentValue.equals(String.valueOf(userId))) {
            return EditLockInfo.builder().configType(configType).configId(configId)
                    .userId(userId).acquired(false).message("锁已失效或被他人持有").build();
        }
        redisTemplate.expire(key, RENEW_TTL.toSeconds(), TimeUnit.SECONDS);
        // 更新持久化记录
        LowCodeEditLock record = editLockMapper.selectOne(new LambdaQueryWrapper<LowCodeEditLock>()
                .eq(LowCodeEditLock::getConfigType, configType)
                .eq(LowCodeEditLock::getConfigId, configId));
        if (record != null) {
            record.setExpireAt(LocalDateTime.now().plus(RENEW_TTL));
            record.setRenewCount(record.getRenewCount() + 1);
            editLockMapper.updateById(record);
        }
        return EditLockInfo.builder()
                .configType(configType).configId(configId).userId(userId)
                .acquiredAt(record != null ? record.getAcquiredAt() : LocalDateTime.now())
                .expireAt(LocalDateTime.now().plus(RENEW_TTL))
                .acquired(true).message("续期成功").build();
    }

    @Override
    public void release(String configType, Long configId, Long userId) {
        String key = buildKey(configType, configId);
        String currentValue = redisTemplate.opsForValue().get(key);
        if (currentValue != null && currentValue.equals(String.valueOf(userId))) {
            redisTemplate.delete(key);
            editLockMapper.delete(new LambdaQueryWrapper<LowCodeEditLock>()
                    .eq(LowCodeEditLock::getConfigType, configType)
                    .eq(LowCodeEditLock::getConfigId, configId));
            log.info("编辑锁释放: {}/{} by user {}", configType, configId, userId);
        }
    }

    @Override
    public EditLockInfo getLock(String configType, Long configId) {
        LowCodeEditLock record = editLockMapper.selectOne(new LambdaQueryWrapper<LowCodeEditLock>()
                .eq(LowCodeEditLock::getConfigType, configType)
                .eq(LowCodeEditLock::getConfigId, configId));
        if (record == null) return null;
        return EditLockInfo.builder()
                .configType(record.getConfigType()).configId(record.getConfigId())
                .userId(record.getUserId()).userName(record.getUserName())
                .acquiredAt(record.getAcquiredAt()).expireAt(record.getExpireAt())
                .acquired(false).build();
    }

    private String buildKey(String configType, Long configId) {
        return LOCK_KEY_PREFIX + configType + ":" + configId;
    }
}
