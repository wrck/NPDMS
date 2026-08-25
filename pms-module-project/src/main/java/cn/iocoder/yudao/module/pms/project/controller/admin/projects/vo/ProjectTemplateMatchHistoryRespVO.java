package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目模板匹配历史 Response VO")
@Data
public class ProjectTemplateMatchHistoryRespVO {
    private Long id;
    private Long projectId;
    private String triggerType;
    private String recordPurpose;
    private String inputOrigin;
    private String snapshotSchemaVersion;
    private String beforeAttributeSnapshot;
    private String attributeSnapshot;
    private String attributeOwnerSnapshot;
    private String sourceOwner;
    private String sourceSystem;
    private String sourceKey;
    private String sourceEventId;
    private String sourceVersion;
    private LocalDateTime sourceOccurredAt;
    private String sourceValueDigest;
    private String mappingVersion;
    private String matcherVersion;
    private String matchResult;
    private String candidateDigest;
    private String decisionMode;
    private Long matchedTemplateId;
    private Long matchedTemplateRevisionId;
    private Long frozenTemplateRevisionId;
    private String impactResult;
    private Long operatorId;
    private String changeReason;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private String idempotencyKey;
    private String requestDigest;
    private String operationId;
    private String traceId;
    private Long auditLogId;
}
