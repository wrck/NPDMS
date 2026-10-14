package cn.iocoder.yudao.module.pms.cutover.controller.business.plan.vo;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 明确的HTTP响应合同；字段保持现行业务投影，不暴露Service内部记录类型。 */
public final class CutoverPlanResponses {
    private CutoverPlanResponses() { }

    public record View(Long taskId, String taskStage, Long taskVersion,
            Long planRevisionId, Integer revisionNo, Long planVersion,
            String originCode, String status, Long sourcePlanRevisionId,
            String revisionReason, JsonNode sourceSnapshot, JsonNode content,
            ApprovalFactView approvalFact, List<String> allowedActions) {
        public static View from(CutoverPlanView value) {
            if (value == null) return null;
            return new View(value.taskId(), value.taskStage(), value.taskVersion(),
                    value.planRevisionId(), value.revisionNo(), value.planVersion(),
                    value.originCode(), value.status(), value.sourcePlanRevisionId(),
                    value.revisionReason(), value.sourceSnapshot(), value.content(),
                    ApprovalFactView.from(value.approvalFact()), value.allowedActions());
        }
    }

    public record ApprovalFactView(Long approvalInstanceId, Long approvalVersion, String status,
            Long decisionAt, String rejectionReason) {
        public static ApprovalFactView from(CutoverPlanView.ApprovalFactView value) {
            if (value == null) return null;
            return new ApprovalFactView(value.approvalInstanceId(), value.approvalVersion(), value.status(),
                    value.decisionAt(), value.rejectionReason());
        }
    }

    public record Download(Long planRevisionId, Long planVersion, FileFact fileArtifactFact,
            Long downloadedAt) {
        public static Download from(DownloadCutoverPlanDraftResult value) {
            if (value == null) return null;
            return new Download(value.planRevisionId(), value.planVersion(), FileFact.from(value.fileArtifactFact()),
                    value.downloadedAt());
        }
    }

    public record Submit(Long taskId, String taskStage, Long taskVersion,
            Long planRevisionId, Integer revisionNo, Long planVersion,
            Long approvalInstanceId, Long approvalVersion, String approvalStatus) {
        public static Submit from(SubmitCutoverPlanResult value) {
            if (value == null) return null;
            return new Submit(value.taskId(), value.taskStage(), value.taskVersion(),
                    value.planRevisionId(), value.revisionNo(), value.planVersion(),
                    value.approvalInstanceId(), value.approvalVersion(), value.approvalStatus());
        }
    }

    public record FileFact(Long artifactId, Integer versionNo, String referenceKey,
            FileFactVersion fileFactVersion, Long scopeVersion, String sha256) {
        public static FileFact from(CutoverPlanFilePort.FileFact value) {
            if (value == null) return null;
            return new FileFact(value.artifactId(), value.versionNo(), value.referenceKey(),
                    FileFactVersion.from(value.fileFactVersion()), value.scopeVersion(), value.sha256());
        }
    }

    public record FileFactVersion(Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion) {
        public static FileFactVersion from(CutoverPlanFilePort.FileFactVersion value) {
            if (value == null) return null;
            return new FileFactVersion(value.artifactVersion(), value.referenceVersion(), value.availabilityVersion());
        }
    }

}
