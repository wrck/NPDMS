package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeGrayRelease;
import com.dp.plat.lowcode.entity.LowCodePublishRecord;
import com.dp.plat.lowcode.mapper.LowCodeGrayReleaseMapper;
import com.dp.plat.lowcode.mapper.LowCodePublishRecordMapper;
import com.dp.plat.lowcode.service.GrayReleaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灰度发布服务实现（批次5-T4，借鉴华为 AppCube / OutSystems LifeTime）。
 *
 * <p>灰度判定逻辑：
 * <ul>
 *   <li>租户白名单优先：tenantId 在白名单中 → 命中</li>
 *   <li>否则按比例：userId hash 取模 100 < grayPercentage → 命中</li>
 *   <li>无活跃灰度或状态非 GRAYING → 不命中</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrayReleaseServiceImpl
        extends ServiceImpl<LowCodeGrayReleaseMapper, LowCodeGrayRelease>
        implements GrayReleaseService {

    private final LowCodePublishRecordMapper publishRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeGrayRelease createGrayRelease(Long publishRecordId, Integer grayPercentage,
                                                 String tenantWhitelist, String createBy) {
        LowCodePublishRecord publishRecord = publishRecordMapper.selectById(publishRecordId);
        if (publishRecord == null) {
            throw new RuntimeException("发布记录不存在: " + publishRecordId);
        }
        if (!"PUBLISHED".equals(publishRecord.getStatus())) {
            throw new RuntimeException("仅 PUBLISHED 状态的发布记录可创建灰度，当前: " + publishRecord.getStatus());
        }
        if (grayPercentage == null || grayPercentage < 0 || grayPercentage > 100) {
            throw new RuntimeException("灰度比例须在 0-100 之间");
        }
        // 同一 configType+configId 仅允许一个活跃灰度
        LowCodeGrayRelease existing = getActiveGrayRelease(publishRecord.getConfigType(), publishRecord.getConfigId());
        if (existing != null) {
            throw new RuntimeException("配置已有活跃灰度发布，须先全量或回滚: grayId=" + existing.getId());
        }

        LowCodeGrayRelease gray = LowCodeGrayRelease.builder()
                .configType(publishRecord.getConfigType())
                .configId(publishRecord.getConfigId())
                .configCode(publishRecord.getConfigCode())
                .version(publishRecord.getVersion())
                .publishRecordId(publishRecordId)
                .grayPercentage(grayPercentage)
                .tenantWhitelist(tenantWhitelist)
                .status("GRAYING")
                .grayStartedAt(LocalDateTime.now())
                .createBy(createBy)
                .build();
        baseMapper.insert(gray);
        log.info("创建灰度发布: config={}/{} v{} percentage={} tenants={} by {}",
                gray.getConfigType(), gray.getConfigId(), gray.getVersion(),
                grayPercentage, tenantWhitelist, createBy);
        return gray;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeGrayRelease updatePercentage(Long id, Integer newPercentage) {
        LowCodeGrayRelease gray = getById(id);
        if (gray == null) throw new RuntimeException("灰度记录不存在: " + id);
        if (!"GRAYING".equals(gray.getStatus())) {
            throw new RuntimeException("仅 GRAYING 状态可调整比例，当前: " + gray.getStatus());
        }
        if (newPercentage == null || newPercentage < 0 || newPercentage > 100) {
            throw new RuntimeException("灰度比例须在 0-100 之间");
        }
        gray.setGrayPercentage(newPercentage);
        baseMapper.updateById(gray);
        log.info("调整灰度比例: id={} {} -> {}", id, gray.getGrayPercentage(), newPercentage);
        return gray;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeGrayRelease releaseFull(Long id) {
        LowCodeGrayRelease gray = getById(id);
        if (gray == null) throw new RuntimeException("灰度记录不存在: " + id);
        if (!"GRAYING".equals(gray.getStatus())) {
            throw new RuntimeException("仅 GRAYING 状态可全量发布，当前: " + gray.getStatus());
        }
        gray.setStatus("FULL");
        gray.setGrayPercentage(100);
        gray.setFullReleasedAt(LocalDateTime.now());
        baseMapper.updateById(gray);
        log.info("灰度全量发布: id={} config={}/{}", id, gray.getConfigType(), gray.getConfigId());
        return gray;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeGrayRelease rollbackGray(Long id) {
        LowCodeGrayRelease gray = getById(id);
        if (gray == null) throw new RuntimeException("灰度记录不存在: " + id);
        if ("ROLLED_BACK".equals(gray.getStatus())) {
            throw new RuntimeException("灰度已回滚，不可重复操作");
        }
        gray.setStatus("ROLLED_BACK");
        gray.setRolledBackAt(LocalDateTime.now());
        baseMapper.updateById(gray);
        log.info("灰度回滚: id={} config={}/{}", id, gray.getConfigType(), gray.getConfigId());
        return gray;
    }

    @Override
    public LowCodeGrayRelease getActiveGrayRelease(String configType, Long configId) {
        List<LowCodeGrayRelease> list = baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeGrayRelease>()
                        .eq(LowCodeGrayRelease::getConfigType, configType)
                        .eq(LowCodeGrayRelease::getConfigId, configId)
                        .eq(LowCodeGrayRelease::getStatus, "GRAYING")
                        .orderByDesc(LowCodeGrayRelease::getCreateTime)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<LowCodeGrayRelease> listByConfig(String configType, Long configId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeGrayRelease>()
                        .eq(LowCodeGrayRelease::getConfigType, configType)
                        .eq(LowCodeGrayRelease::getConfigId, configId)
                        .orderByDesc(LowCodeGrayRelease::getCreateTime));
    }

    @Override
    public boolean isInGray(String configType, Long configId, Long userId, String tenantId) {
        LowCodeGrayRelease gray = getActiveGrayRelease(configType, configId);
        if (gray == null) {
            return false;
        }
        // 1. 租户白名单优先
        if (tenantId != null && gray.getTenantWhitelist() != null && !gray.getTenantWhitelist().isBlank()) {
            try {
                JsonNode arr = objectMapper.readTree(gray.getTenantWhitelist());
                if (arr.isArray()) {
                    for (JsonNode t : arr) {
                        if (t.isTextual() && tenantId.equals(t.asText())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析租户白名单失败: {}", gray.getTenantWhitelist(), e);
            }
        }
        // 2. 按比例（userId hash 取模 100 < grayPercentage）
        if (userId != null && gray.getGrayPercentage() > 0) {
            int hash = Math.abs(userId.hashCode() % 100);
            return hash < gray.getGrayPercentage();
        }
        return false;
    }
}
