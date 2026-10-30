package cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;

import java.util.List;

/**
 * 公共审批关联的回调入口：审批引擎产生可信结果后调用，
 * 公共服务校验后端身份、租户、实例关联、业务主体和尝试后幂等更新。
 * 批次回调按主体逐行更新，单主体校验失败跳过并如实报告，不虚构整批成功。
 */
public interface ApprovalAssociationApi {

    ApprovalRecordingResult recordTrustedResult(ApprovalTrustedResult result);

    void recordWithdrawal(String backendId, String instanceRef, String attemptId, String reason);

    /** 批次回调结果：updated 为幂等更新行数，skipped 为因校验未通过而未更新的主体。 */
    record ApprovalRecordingResult(int updated, List<String> skippedReasons) {
        public ApprovalRecordingResult {
            skippedReasons = skippedReasons == null ? List.of() : List.copyOf(skippedReasons);
        }

        public int skipped() {
            return skippedReasons.size();
        }
    }
}
