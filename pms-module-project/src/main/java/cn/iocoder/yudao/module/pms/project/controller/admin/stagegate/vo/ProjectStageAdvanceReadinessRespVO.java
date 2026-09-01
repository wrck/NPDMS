package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessResult;

import java.util.List;

public record ProjectStageAdvanceReadinessRespVO(
        Long projectId, Integer projectVersion, Long treeVersion,
        String currentStage, String nextStage, boolean advanceAllowed,
        String guidance, List<Gate> gates) {

    public static ProjectStageAdvanceReadinessRespVO from(ProjectStageReadinessResult result) {
        return new ProjectStageAdvanceReadinessRespVO(result.projectId(), result.projectVersion(),
                result.treeVersion(), result.currentStage(), result.nextStage(), result.advanceAllowed(),
                result.guidance(), result.gates().stream().map(Gate::from).toList());
    }

    public record Gate(Long gateId, String gateCode, String name, String status,
                       boolean satisfied, List<Reference> references) {
        static Gate from(ProjectStageReadinessResult.GateResult result) {
            return new Gate(result.gateId(), result.gateCode(), result.name(), result.status(),
                    result.satisfied(), result.references().stream().map(Reference::from).toList());
        }
    }

    public record Reference(Long gateReferenceId, String refType, String refCode,
                            ProjectStageGateFact fact, List<String> allowedActions) {
        static Reference from(ProjectStageReadinessResult.ReferenceResult result) {
            return new Reference(result.gateReferenceId(), result.refType(), result.refCode(),
                    result.fact(), result.allowedActions());
        }
    }
}
