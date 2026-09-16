package cn.iocoder.yudao.module.pms.platform.api.spi;

import java.util.Map;

/**
 * 业务数据加载器 SPI（迁移自源工程 pms-common com.dp.plat.common.spi.BusinessDataLoader，
 * Story 6 / TD-P8-001）。
 *
 * <p>审批中心需要按审批类型加载对应业务对象的字段用于脱敏展示。本接口为扩展点，
 * 各业务模块（交付件/风险/变更/项目等）可实现各自的加载器并注册为 Spring Bean，
 * 审批中心通过 Spring 自动注入收集所有实现并按 {@link #supportedType()} 路由。</p>
 *
 * <p>本接口原位于源工程 pms-workflow 模块，因 pms-project 与 pms-workflow
 * 存在双向依赖环（TD-P8-001），下沉到 pms-common 解耦。各业务模块可直接实现本接口，
 * 无需依赖 pms-workflow。</p>
 */
public interface BusinessDataLoader {

    /**
     * 加载业务对象为字段 Map。
     *
     * @param approvalType 审批类型（PROJECT/TASK/DELIVERABLE/RISK/ISSUE/CHANGE/...）
     * @param businessId   业务对象ID
     * @return 字段 Map（fieldName → value），无数据返回空 Map
     */
    Map<String, Object> load(String approvalType, Long businessId);

    /**
     * 本加载器支持的审批类型（用于路由匹配）。
     */
    String supportedType();
}