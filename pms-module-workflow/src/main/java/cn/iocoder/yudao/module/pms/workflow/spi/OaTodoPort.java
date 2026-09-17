package cn.iocoder.yudao.module.pms.workflow.spi;

import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;

/**
 * OA 待办推送端口：workflow 声明 SPI，integration 实现并注册 Spring Bean，
 * workflow 不直接依赖 integration 的 Service。集成模块未加载时按 best-effort 跳过。
 *
 * <p>实现必须在独立事务（REQUIRES_NEW）中执行 OA 调用并持久化集成日志。
 * 受控外部调用失败应保留失败日志后向上抛出；数据库故障不承诺日志已保存。
 * 调用方在事务代理之外捕获异常，不阻塞工作流主流程。</p>
 */
public interface OaTodoPort {

    /** 推送 OA 待办，字段保持 OaTodoCommand → OaTodoRequest 一一映射。 */
    void pushTodo(OaTodoCommand command);

    /**
     * 完成 OA 待办，必须使用创建时对应的业务键。
     * 兼容既有 process variable businessKey；未提供时使用 Flowable 任务 ID。
     */
    void completeTodo(String businessKey);
}
