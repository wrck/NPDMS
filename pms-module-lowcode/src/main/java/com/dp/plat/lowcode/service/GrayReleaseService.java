package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeGrayRelease;

import java.util.List;

/**
 * 灰度发布服务（批次5-T4）。
 */
public interface GrayReleaseService extends IService<LowCodeGrayRelease> {

    /**
     * 创建灰度发布策略（基于已审批通过的发布记录）。
     *
     * @param publishRecordId  发布记录 ID（须为 PUBLISHED 状态）
     * @param grayPercentage   灰度比例 0-100
     * @param tenantWhitelist  租户白名单 JSON 字符串（如 '["tenant1","tenant2"]'），可为 null
     * @param createBy         创建人
     * @return 灰度发布记录
     */
    LowCodeGrayRelease createGrayRelease(Long publishRecordId, Integer grayPercentage,
                                          String tenantWhitelist, String createBy);

    /**
     * 调整灰度比例（仅 GRAYING 状态可调整）。
     */
    LowCodeGrayRelease updatePercentage(Long id, Integer newPercentage);

    /**
     * 全量发布（percentage 置 100，status 置 FULL）。
     */
    LowCodeGrayRelease releaseFull(Long id);

    /**
     * 回滚灰度（status 置 ROLLED_BACK）。
     */
    LowCodeGrayRelease rollbackGray(Long id);

    /**
     * 查询指定配置的活跃灰度（status = GRAYING）。
     */
    LowCodeGrayRelease getActiveGrayRelease(String configType, Long configId);

    /**
     * 查询指定配置的全部灰度记录。
     */
    List<LowCodeGrayRelease> listByConfig(String configType, Long configId);

    /**
     * 判定用户/租户是否命中灰度（用于运行时路由判断）。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @param userId     用户 ID（用于按 userId hash 取模判断比例命中）
     * @param tenantId   租户 ID（用于白名单匹配）
     * @return true 表示命中灰度，应使用新版本
     */
    boolean isInGray(String configType, Long configId, Long userId, String tenantId);
}
