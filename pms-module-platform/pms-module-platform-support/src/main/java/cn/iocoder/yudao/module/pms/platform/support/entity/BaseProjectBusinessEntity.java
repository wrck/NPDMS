package cn.iocoder.yudao.module.pms.platform.support.entity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
/** Explicit ordinary project ownership; legacy entities are not enrolled by guessing field names. */
@lombok.EqualsAndHashCode(callSuper=true)
public abstract class BaseProjectBusinessEntity extends BaseBusinessEntity {
    @NotNull @Positive @BusinessModelField(name="项目")
    private Long projectId;
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}
