package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_spare_application_reference")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverSpareApplicationReferenceDO extends BaseBusinessEntity {
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Long projectId;
    private String platformRequestId;
    @TableField(value = "integration_status_code")
    private String integrationStatus;
    private String externalSystemCode;
    private String externalRequestId;
    private String externalApplicationNo;
    private String launchUrl;
    @TableField(exist = false)
    private String needSnapshot;
    @TableField(exist = false)
    private String requestContextSnapshot;
    private Long currentStatusRevisionId;
    private Integer retryCount;
    private String lastFailureCode;
    private String lastFailureDetail;
    @TableField(value = "last_attempt_time")
    private LocalDateTime lastAttemptAt;


    /** 当时备件需求判断结论；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Boolean needRequiredFlag;
    /** 当时割接任务号；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String taskNo;
    /** 当时任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceTaskVersion;
    /** 原请求跟踪标识；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String correlationId;
}
