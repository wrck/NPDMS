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
@TableName("sol_requirement_analysis_revision")
public class RequirementAnalysisRevisionDO extends RequirementAnalysisDO implements EntityRevision {
    private Long entityId;
    private Integer revisionNo;
    private Long sourceRevisionId;
    private Long baseEffectiveRevisionId;
    private Integer baseEntityVersion;
    private String revisionState;
    private Integer draftMarker;
    private Integer effectiveMarker;
    private String changeReason;
    private Long frozenBy;
    private LocalDateTime frozenAt;
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
