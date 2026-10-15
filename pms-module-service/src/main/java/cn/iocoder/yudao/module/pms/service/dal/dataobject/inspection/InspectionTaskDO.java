package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_task")
public class InspectionTaskDO extends BaseBusinessEntity {
    private String taskCode;
    private String taskName;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private Long ownerUserId;
    private String background;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime plannedAt;
    private String mode;
    private String statusCode;
    private Long currentAttemptId;
    private Long currentReportId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long archiveRequestedBy;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime archiveRequestedAt;
    private Long archivedBy;
    private java.time.LocalDateTime archivedAt;
    private Long customerArchiveId;
    private String cancellationReason;
}
