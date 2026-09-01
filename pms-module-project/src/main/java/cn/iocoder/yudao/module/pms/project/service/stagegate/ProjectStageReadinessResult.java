package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;

import java.util.List;

public record ProjectStageReadinessResult(
        Long projectId, Integer projectVersion, Long treeVersion,
        String currentStage, String nextStage, boolean advanceAllowed,
        String guidance, List<GateResult> gates) {

    public record GateResult(Long gateId, String gateCode, String name, String status,
                             boolean satisfied, List<ReferenceResult> references) {
    }

    public record ReferenceResult(Long gateReferenceId, String refType, String refCode,
                                  ProjectStageGateFact fact, List<String> allowedActions) {
    }
}
