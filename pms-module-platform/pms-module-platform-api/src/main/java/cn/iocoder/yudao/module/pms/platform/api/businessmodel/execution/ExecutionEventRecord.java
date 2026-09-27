package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.evidence.ExecutionEvidenceRecord;

/**
 * 执行事件：由执行后端产生，经公共结果校验后进入权威业务事实；
 * 过期事件不改投其他轮次，切换后端不改变已采纳的结果身份。
 */
public record ExecutionEventRecord(
        String executionRef,
        ExecutionEvidenceRecord evidence,
        String kind,
        String payloadDigest) {
}
