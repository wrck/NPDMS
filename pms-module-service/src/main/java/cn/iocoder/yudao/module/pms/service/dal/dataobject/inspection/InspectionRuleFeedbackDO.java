package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_rule_feedback")
public class InspectionRuleFeedbackDO extends BaseBusinessEntity {
    private Long taskId;
    private Long deviceId;
    private String description;
    private Long reportedBy;
    private java.time.LocalDateTime reportedAt;
}
