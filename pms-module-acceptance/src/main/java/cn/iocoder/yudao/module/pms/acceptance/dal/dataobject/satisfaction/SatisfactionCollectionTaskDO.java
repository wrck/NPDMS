package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_satisfaction_collection_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionCollectionTaskDO extends BaseBusinessEntity {
    private Long projectId;
    private Long projectTaskId;
    private Long deliverableId;
    private String originKind;
    private String originKey;
    private String originSnapshot;
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
}
