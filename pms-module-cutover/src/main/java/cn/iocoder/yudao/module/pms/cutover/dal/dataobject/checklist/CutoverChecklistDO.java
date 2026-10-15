package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_checklist")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistDO extends BaseBusinessEntity {
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Long assessmentId;
    private Long assessmentVersion;
    private Long checklistVersion;
    private String statusCode;
    @TableField(exist = false)
    private String inputSnapshot;
    private String inputSnapshotHash;
    @TableField(exist = false)
    private String configRevisionSnapshot;
    @TableField(exist = false)
    private String matchTrace;
    @TableField(exist = false)
    private String configGapSnapshot;
    @TableField(value = "submit_user_id")
    private Long submittedBy;
    @TableField(value = "submit_time")
    private LocalDateTime submittedAt;
    @TableField(value = "invalidate_time")
    private LocalDateTime invalidatedAt;
    private String invalidatedReason;
    private Integer currentMarker;


    /** 生成时任务并发版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceTaskVersion;
    /** 生成时项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectScopeVersion;
    /** 匹配项目主数据维度时由项目 Owner 锁定的版本；旧配置未消费该维度时为空。 */
    private Long matchingProjectVersion;
    /** 消费的配置修订ID；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long configurationRevisionId;
    /** 消费的配置编码；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String configurationCode;
    /** 消费的配置修订号；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Integer configurationRevisionNo;
    /** 原缺口原因编码 */
    private String configGapReasonCode;
}
