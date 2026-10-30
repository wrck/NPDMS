package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

/**
 * 项目编排端口：中性过程定义、稳定业务实例/节点引用、已验证事实、操作请求和执行事件。
 * 具体后端表达及原生执行标识仅进入绑定/关联记录，不成为实体、规则或公共操作必填字段。
 */
public interface ProjectOrchestrationPort {

    String instantiate(ProcessDefinitionRef definition, EntityRef businessInstance, String idempotencyKey);

    void reportExecutionEvent(ExecutionEventRecord event);
}
