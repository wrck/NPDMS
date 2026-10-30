package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_task_rule_snapshot")
public class InspectionTaskRuleSnapshotDO extends BaseBusinessEntity {
    private Long taskId;
    private Long attemptId;
    private Long deviceId;
    private Long ruleRevisionId;
}
