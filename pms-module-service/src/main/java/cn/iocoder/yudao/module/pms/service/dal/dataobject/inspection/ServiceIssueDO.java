package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_service_issue")
public class ServiceIssueDO extends BaseBusinessEntity {
    private Long taskId;
    private Long reportId;
    private Long resultId;
    private Long annotationId;
    private Long responsibleUserId;
    private java.time.LocalDateTime dueAt;
    private String description;
    private String severityCode;
    private String statusCode;
    private Long closureRequestedBy;
    private java.time.LocalDateTime closureRequestedAt;
    private String closureDescription;
    private Long closedBy;
    private java.time.LocalDateTime closedAt;
}
