package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRevision;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * Reuses the business entity's typed fields, tenant, audit and optimistic lock.
 * Inherited id identifies this revision; entityId identifies the current business object.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value="sol_requirement_analysis_revision",autoResultMap=true)
@cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel(ownerModule="SOL",entityType="requirementAnalysisRevision",stableCode="SOL_REQUIREMENT_ANALYSIS_REVISION",name="需求分析",permissionPrefix="pms:requirement-analysis",nativeEntityType="REQUIREMENT_ANALYSIS_REVISION")
public class RequirementAnalysisRevisionDO extends RequirementAnalysisDO implements EntityRevision {
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="逻辑实体ID",writable=false) private Long entityId;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="修订号",writable=false) private Integer revisionNo;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="来源修订",writable=false) private Long sourceRevisionId;
    private Long baseEffectiveRevisionId;
    private Integer baseEntityVersion;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="修订状态",writable=false) private String revisionState;
    private Integer draftMarker;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="当前生效",writable=false) private Integer effectiveMarker;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="变更原因",writable=false) private String changeReason;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="冻结人",writable=false) private Long frozenBy;
    @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField(name="冻结时间",writable=false) private LocalDateTime frozenAt;
    private Long projectTemplateId;
    private Long projectTemplateRevisionId;
    private String executionSnapshot;

    @Override
    public EntityRef entityRef() {
        return new EntityRef(getTenantId(), "SOL", "REQUIREMENT_ANALYSIS", getEntityId());
    }

    @Override
    public EntityVersionProvider.Revision.State revisionState() {
        return EntityVersionProvider.Revision.State.valueOf(getRevisionState());
    }

    @Override
    public boolean effective() {
        return Integer.valueOf(1).equals(getEffectiveMarker());
    }
}
