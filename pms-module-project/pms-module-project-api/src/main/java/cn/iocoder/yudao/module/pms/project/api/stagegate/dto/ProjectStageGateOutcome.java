package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** 阶段门禁Owner事实的封闭结果。 */
public enum ProjectStageGateOutcome {
    SATISFIED,
    UNSATISFIED,
    VERSION_CONFLICT,
    DEPENDENCY_UNAVAILABLE
}
