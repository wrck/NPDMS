package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_attempt")
public class InspectionAttemptDO extends BaseBusinessEntity {
    private Long taskId;
    private Integer attemptNo;
    private String mode;
    private String statusCode;
    private String reason;
    private Long startedBy;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime finishedAt;
}
