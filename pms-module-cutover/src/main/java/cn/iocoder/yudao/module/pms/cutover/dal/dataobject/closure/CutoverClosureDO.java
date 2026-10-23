package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_closure")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverClosureDO extends BaseBusinessEntity {
    private Long taskId;
    private Long projectId;
    private Long approvalInstanceId;
    private Long approvalVersion;
    private Long planRevisionId;
    private Integer planRevisionNo;
    private Long planVersion;
    private Long taskVersionAtP6;
    @TableField(exist = false)
    private String deviceScopeWatermark;
    private String statusCode;
    @TableField(value = "pre_check_normal_flag")
    private Boolean preCheckNormal;
    private String preCheckDetail;
    @TableField(value = "execution_normal_flag")
    private Boolean executionNormal;
    private String executionDetail;
    @TableField(value = "test_normal_flag")
    private Boolean testNormal;
    private String testDetail;
    @TableField(value = "rollback_occurred_flag")
    private Boolean rollbackOccurred;
    @TableField(value = "rollback_successful_flag")
    private Boolean rollbackSuccessful;
    private String rollbackReason;
    private String legacyItems;
    private String finalResultCode;
    private String resultRef;
    @TableField(value = "submit_user_id")
    private Long submittedBy;
    @TableField(value = "submit_time")
    private LocalDateTime submittedAt;
    @TableField(value = "archive_time")
    private LocalDateTime archivedAt;
}
