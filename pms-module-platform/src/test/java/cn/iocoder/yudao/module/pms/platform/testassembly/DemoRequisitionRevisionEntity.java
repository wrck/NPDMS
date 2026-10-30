package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.MutableEntityRevision;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 仅测试装配的演示申领单修订实体：继承业务字段并统一实现可变修订元数据，
 * 由继承式内容历史通用适配承载，不为本实体手写版本 Provider。
 * 修订元数据列标注 @JsonIgnore，避免进入开放业务字段目录。
 */
@TableName("pms_plat_demo_requisition_revision")
public class DemoRequisitionRevisionEntity extends DemoRequisitionEntity implements MutableEntityRevision {

    @JsonIgnore
    private Long entityId;
    @JsonIgnore
    private Integer revisionNo;
    @JsonIgnore
    private Long sourceRevisionId;
    @JsonIgnore
    private Long baseEffectiveRevisionId;
    @JsonIgnore
    private Integer baseEntityVersion;
    @JsonIgnore
    private String changeReason;
    @JsonIgnore
    private Long frozenBy;
    @JsonIgnore
    private LocalDateTime frozenAt;
    @JsonIgnore
    @TableField("revision_state")
    private String revisionStateText;
    @JsonIgnore
    private Boolean effective;

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
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

    @Override
    public EntityVersionProvider.Revision.State revisionState() {
        return revisionStateText == null ? null : EntityVersionProvider.Revision.State.valueOf(revisionStateText);
    }

    @Override
    public void setRevisionState(EntityVersionProvider.Revision.State state) {
        this.revisionStateText = state == null ? null : state.name();
    }

    @Override
    public boolean effective() {
        return Boolean.TRUE.equals(effective);
    }

    public void setEffective(boolean effective) {
        this.effective = effective;
    }

    public Boolean getEffective() {
        return effective;
    }

    public void setEffective(Boolean effective) {
        this.effective = effective;
    }

    @Override
    public EntityRef entityRef() {
        return new EntityRef(getTenantId(), "demo", "requisition", entityId);
    }
}
