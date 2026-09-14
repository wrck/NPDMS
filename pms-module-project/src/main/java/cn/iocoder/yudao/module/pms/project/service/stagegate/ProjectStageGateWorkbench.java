package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import java.util.List;

/** Read model only: rule outcomes are distinct from persisted gate status and grant no write permission. */
public record ProjectStageGateWorkbench(Long projectId, Integer projectVersion, Long planVersionId,
        Long stageId, String stageCode, Long executionId, Integer executionRound, String recoverableError,
        List<Gate> gates) {
    public record Gate(Long gateId, String gateCode, String name, String gateType, String persistedStatus,
                       RuleEvaluation evaluation, List<Reference> references) { }
    public record Reference(Long gateReferenceId, String refType, String refCode, String refVersion,
                            ProjectStageGateProcessState process, boolean canStart) { }
}
