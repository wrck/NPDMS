package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** PM-07模板匹配决策与影响评估永久历史。 */
@TableName("proj_project_template_match_history")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateMatchHistoryDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
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
