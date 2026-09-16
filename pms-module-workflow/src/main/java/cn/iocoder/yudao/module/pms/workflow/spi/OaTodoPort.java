package cn.iocoder.yudao.module.pms.workflow.spi;

import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;

/**
 * OA 待办推送端口（本模块 SPI，迁移自源工程 pms-workflow OaTaskListener 对
 * pms-integration OaIntegrationService 的直接依赖，按模块边界做依赖倒置）。
 *
 * <p>源工程 pms-workflow 直接依赖 pms-integration 的 Service 违反目标模块边界
 * （模块间不得依赖其他模块的 Service）。本模块声明扩展点，由集成侧实现并注册为
 * Spring Bean；若集成模块未加载（bean 不存在），OA 同步为 best-effort 跳过。</p>
 *
 * <p>实现应在独立事务（REQUIRES_NEW）中执行 OA 调用并持久化集成日志，
 * 调用失败时向上抛出异常，由调用方（OaTaskListener）catch 吞掉，不阻塞工作流主流程。</p>
 */
public interface OaTodoPort {

    /**
     * 推送 OA 待办。
     *
     * @param command OA 待办命令（title/content/handlerUserId/processInstanceId/
     *                businessKey/processUrl/businessType）
     */
    void pushTodo(OaTodoCommand command);

    /**
     * 完成 OA 待办。
     *
     * @param businessKey OA 待办的业务键（Flowable 场景下为任务 ID）
     */
    void completeTodo(String businessKey);
}