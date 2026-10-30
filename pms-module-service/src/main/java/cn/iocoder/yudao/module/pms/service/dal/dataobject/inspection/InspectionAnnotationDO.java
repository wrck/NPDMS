package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_annotation")
public class InspectionAnnotationDO extends BaseBusinessEntity {
    private Long taskId;
    private Long reportId;
    private Long resultId;
    private Integer revisionNo;
    private String kind;
    private String reason;
    private String changeReason;
    private Long responsibleUserId;
    private java.time.LocalDateTime dueAt;
    private Long annotatedBy;
    private java.time.LocalDateTime annotatedAt;
}
