package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_instance")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalInstanceDO extends BaseBusinessEntity {
    private Long taskId;
    private Long projectId;
    private Long planRevisionId;
    private Integer planRevisionNo;
    private Long assessmentId;
    private Long assessmentVersion;
    private Long checklistId;
    private Long checklistVersion;
    private String gradeCode;
    private Long initiatorUserId;
    private Long initiatorProjectScopeVersion;
    private Integer sourceSnapshotVersion;
    private String sourceSnapshot;
    private String routeSnapshot;
    private Boolean leadTimeEnabled;
    private String leadTimeSnapshot;
    private String statusCode;
    private String holdReasonCode;
    private Integer currentNodeNo;
    private Long previousApprovalInstanceId;
    private Long replacementApprovalInstanceId;
    private LocalDateTime decisionAt;
    private String rejectionReason;
}
