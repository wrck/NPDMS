package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_task_event")
public class InspectionTaskEventDO extends BaseBusinessEntity {
    private Long taskId;
    private String actionCode;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String description;
    private java.time.LocalDateTime occurredAt;
}
