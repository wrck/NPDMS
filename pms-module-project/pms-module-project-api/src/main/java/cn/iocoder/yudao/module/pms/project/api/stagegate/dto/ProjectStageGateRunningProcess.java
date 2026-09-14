package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** Owner-confirmed unfinished work; no approval outcome or business payload is exposed. */
public record ProjectStageGateRunningProcess(String processInstanceId, Long gateReferenceId, String stageCode) { }
