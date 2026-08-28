package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.util.List;

@Data
public class RequirementAnalysisWorkspaceRespVO {
    private Long projectId;
    private RequirementAnalysisVersionRespVO currentEffective;
    private RequirementAnalysisVersionRespVO draft;
    private List<String> allowedActions;
}
