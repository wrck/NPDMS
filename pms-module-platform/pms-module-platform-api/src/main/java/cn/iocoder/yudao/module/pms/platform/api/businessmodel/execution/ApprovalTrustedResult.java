package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

/**
 * 可信流程结果：由所选审批引擎产生，公共服务校验后端身份、租户、实例关联、
 * 业务主体和尝试后幂等更新；"流程批准"与"领域生效"分别记录。
 */
public record ApprovalTrustedResult(
        String backendId,
        String processRef,
        String instanceRef,
        String attemptId,
        boolean approved,
        String conclusionBasis) {
}
