package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_satisfaction_collection_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionCollectionTaskDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private Long projectTaskId;
    private String sourceOwnerContext;
    private String sourceObjectType;
    private String sourceObjectId;
    private Long sourceObjectVersion;
    private String triggerOwnerContext;
    private String triggerObjectType;
    private String triggerFactId;
    private Long triggerFactVersion;
    private String collectionKey;
    private Integer taskRevisionNo;
    private Long priorTaskId;
    private Long assignedToUserId;
    private Long assignedByUserId;
    private String taskStatus;
    private Long questionnaireId;
    private Long resultId;
    private Integer version;
}
