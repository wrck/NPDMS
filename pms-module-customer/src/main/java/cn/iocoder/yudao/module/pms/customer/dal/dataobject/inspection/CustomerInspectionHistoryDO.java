package cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cus_inspection_history")
public class CustomerInspectionHistoryDO extends BaseBusinessEntity {
    private Long customerId;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long taskId;
    private Long taskVersion;
    private String taskCode;
    private String taskName;
    private Long reportId;
    private String sourceFingerprint;
    private Long archivedBy;
    private LocalDateTime archivedAt;
}
