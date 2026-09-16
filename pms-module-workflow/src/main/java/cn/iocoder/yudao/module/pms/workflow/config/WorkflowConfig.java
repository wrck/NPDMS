package cn.iocoder.yudao.module.pms.workflow.config;

import org.springframework.context.annotation.Configuration;

/**
 * Flowable workflow engine configuration（迁移自源工程 pms-workflow WorkflowConfig）。
 *
 * <p>源工程的 {@code ProcessEngineConfigurationConfigurer} 设置了
 * {@code databaseSchemaUpdate="true"} 与 {@code HistoryLevel.FULL}。
 * 目标体系下引擎由 yudao-server 统一装配（application.yaml 的
 * {@code flowable.database-schema-update} 与 {@code flowable.history-level}），
 * 且 yudao-module-bpm 已通过 {@code BpmFlowableConfiguration} 注册自己的
 * {@code EngineConfigurationConfigurer}。本模块不再覆盖引擎级配置：
 * schema 变更走 flowable-sql 初始化（Flyway 管理的迁移链），
 * history 级别沿用 server 配置（audit），避免影响 BPM 模块的既有行为。</p>
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowConfig {
}