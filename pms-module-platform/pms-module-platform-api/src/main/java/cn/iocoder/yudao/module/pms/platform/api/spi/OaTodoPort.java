package cn.iocoder.yudao.module.pms.platform.api.spi;

import cn.iocoder.yudao.module.pms.platform.api.spi.dto.OaTodoCommand;

/**
 * OA 待办推送端口。
 *
 * <p>该端口位于平台 API 层，作为 workflow 与 integration 之间的稳定跨模块契约：
 * workflow 负责发布待办意图，integration 负责对接具体 OA 系统。双方均不依赖对方的
 * 实现模块。</p>
 *
 * <p>实现方应保证 OA 调用失败不会破坏工作流主事务；具体重试、日志与事务策略由
 * integration 侧负责。</p>
 */
public interface OaTodoPort {

    /**
     * 推送 OA 待办。
     *
     * @param command OA 待办命令
     */
    void pushTodo(OaTodoCommand command);

    /**
     * 完成 OA 待办。
     *
     * @param businessKey OA 待办业务键
     */
    void completeTodo(String businessKey);
}
