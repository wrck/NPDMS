package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_feedback_confirmation")
public class InspectionFeedbackConfirmationDO extends BaseBusinessEntity {
    private Long taskId;
    private Long annotationId;
    private Integer revisionNo;
    private Boolean validFlag;
    private String reason;
    private Long confirmedBy;
    private java.time.LocalDateTime confirmedAt;
}
