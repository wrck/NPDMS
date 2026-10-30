package cn.iocoder.yudao.module.pms.platform.support.it;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRevision;

import java.time.LocalDateTime;

/**
 * 订单修订实体：继承业务字段并实现 EntityRevision；修订元数据列独立于当前对象。
 */
@TableName("pms_plat_it_order_revision")
public class ItOrderRevisionEntity extends ItOrderEntity implements EntityRevision {

    private Long entityId;

    private Integer revisionNo;

    private Long sourceRevisionId;

    private Long baseEffectiveRevisionId;

    private Integer baseEntityVersion;

    private String revisionState;

    private Boolean effective;

    private String changeReason;

    private Long frozenBy;

    private LocalDateTime frozenAt;

    @Override
    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    @Override
    public EntityRef entityRef() {
        return new EntityRef(getTenantId(), "it", "order", entityId);
    }

    @Override
    public EntityVersionProvider.Revision.State revisionState() {
        return revisionState == null ? null : EntityVersionProvider.Revision.State.valueOf(revisionState);
    }

    @Override
    public boolean effective() {
        return Boolean.TRUE.equals(effective);
    }

    public Integer getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(Integer revisionNo) {
        this.revisionNo = revisionNo;
    }

    public Long getSourceRevisionId() {
        return sourceRevisionId;
    }

    public void setSourceRevisionId(Long sourceRevisionId) {
        this.sourceRevisionId = sourceRevisionId;
    }

    public Long getBaseEffectiveRevisionId() {
        return baseEffectiveRevisionId;
    }

    public void setBaseEffectiveRevisionId(Long baseEffectiveRevisionId) {
        this.baseEffectiveRevisionId = baseEffectiveRevisionId;
    }

    public Integer getBaseEntityVersion() {
        return baseEntityVersion;
    }

    public void setBaseEntityVersion(Integer baseEntityVersion) {
        this.baseEntityVersion = baseEntityVersion;
    }

    public String getRevisionState() {
        return revisionState;
    }

    public void setRevisionState(String revisionState) {
        this.revisionState = revisionState;
    }

    public Boolean getEffective() {
        return effective;
    }

    public void setEffective(Boolean effective) {
        this.effective = effective;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public Long getFrozenBy() {
        return frozenBy;
    }

    public void setFrozenBy(Long frozenBy) {
        this.frozenBy = frozenBy;
    }

    public LocalDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDateTime frozenAt) {
        this.frozenAt = frozenAt;
    }
}
