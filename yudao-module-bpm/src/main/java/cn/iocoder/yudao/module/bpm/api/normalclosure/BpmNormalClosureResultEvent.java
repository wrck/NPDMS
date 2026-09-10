package cn.iocoder.yudao.module.bpm.api.normalclosure;

/** A notification to re-read BPM history, never an approval command or a business completion fact. */
public record BpmNormalClosureResultEvent(Long tenantId, String processInstanceId) { }
