package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_service_issue_remediation")
public class ServiceIssueRemediationDO extends BaseBusinessEntity {
    private Long taskId;
    private Long issueId;
    private String actionCode;
    private String content;
    private Long actorId;
    private java.time.LocalDateTime actionAt;
}
