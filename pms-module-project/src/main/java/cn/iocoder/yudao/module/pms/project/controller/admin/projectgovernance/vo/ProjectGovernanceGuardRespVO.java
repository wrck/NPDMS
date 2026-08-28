package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目治理守卫响应")
@Data
public class ProjectGovernanceGuardRespVO {

    private Long projectId;
    private Integer projectVersion;
    private String lifecycleStatus;
    private String currentStage;
    private String assignmentStatus;
    private Long treeRootProjectId;
    private Long treeVersion;
    private String action;
    private Boolean allowed;
    private String guardToken;
    private List<ProviderFact> providerFacts;
    private List<Blocker> blockers;
    private Long blockerTotal;
    private Integer blockerPageNo;
    private Integer blockerPageSize;
    private LocalDateTime checkedAt;

    @Data
    public static class ProviderFact {
        private String provider;
        private String factVersion;
        private String watermark;
        private String factDigest;
    }

    @Data
    public static class Blocker {
        private String provider;
        private String objectType;
        private String objectId;
        private String status;
        private String code;
        private String summary;
    }
}
