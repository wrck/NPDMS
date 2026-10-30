package cn.iocoder.yudao.module.pms.bindings.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;

/**
 * 场景执行结果：判定结论、形成的结果身份与回执；两种后端输出同一结构。
 */
public record ScenarioRunOutcome(
        String backendId,
        String definitionCode,
        Integer definitionVersion,
        String verdict,
        String resultId,
        BusinessResultRecord result,
        boolean replay) {
}
