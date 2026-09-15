package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** 阶段和任务的公共实例字段；两类实例仍独立持久化，不承载模板定义或状态转换规则。 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ProjectExecutionNodeDO<T extends ProjectExecutionNodeDO<T>> extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private String name;
    private Integer sortOrder;
    private Long sourceDefinitionId;
    /** 实例建议开始时间，不由模板配置。 */
    private LocalDateTime suggestedStartTime;
    /** 实例建议结束时间，不由模板配置。 */
    private LocalDateTime suggestedEndTime;
    /** 实例验收时间，与实际结束时间独立；未验收时为空。 */
    private LocalDateTime acceptanceTime;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String status;
    private Integer version;

    /** 自身编码。子类仅声明各自既有数据库列的映射。 */
    public abstract String getCode();
    public abstract T setCode(String code);
    protected abstract T self();

    public T setId(Long value) { id = value; return self(); }
    public T setProjectId(Long value) { projectId = value; return self(); }
    public T setName(String value) { name = value; return self(); }
    public T setSortOrder(Integer value) { sortOrder = value; return self(); }
    public T setSourceDefinitionId(Long value) { sourceDefinitionId = value; return self(); }
    public T setSuggestedStartTime(LocalDateTime value) { suggestedStartTime = value; return self(); }
    public T setSuggestedEndTime(LocalDateTime value) { suggestedEndTime = value; return self(); }
    public T setAcceptanceTime(LocalDateTime value) { acceptanceTime = value; return self(); }
    public T setPlanStartTime(LocalDateTime value) { planStartTime = value; return self(); }
    public T setPlanEndTime(LocalDateTime value) { planEndTime = value; return self(); }
    public T setActualStartTime(LocalDateTime value) { actualStartTime = value; return self(); }
    public T setActualEndTime(LocalDateTime value) { actualEndTime = value; return self(); }
    public T setStatus(String value) { status = value; return self(); }
    public T setVersion(Integer value) { version = value; return self(); }
}
