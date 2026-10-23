package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_feedback_export_item")
public class InspectionFeedbackExportItemDO extends BaseBusinessEntity {
    private Long exportId, projectId, annotationId, confirmationId;
    private Boolean activeFlag;
}
