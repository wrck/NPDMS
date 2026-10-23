package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_plan_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanRevisionDO extends BaseBusinessEntity {
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Integer revisionNo;
    private String originCode;
    private String editModeCode;
    private String gradeCode;
    private Long assessmentId;
    private Long assessmentVersion;
    private Long checklistId;
    private Long checklistVersion;
    private Long configurationRevisionId;
    private String configurationCode;
    private Integer configurationRevisionNo;
    @TableField(exist = false)
    private String templateSectionSnapshot;
    @TableField(exist = false)
    private String sourceSnapshot;
    @TableField(exist = false)
    private String contentSnapshot;
    private Long fileArtifactId;
    private Integer fileVersionNo;
    private String fileReferenceKey;
    @TableField(exist = false)
    private String fileFactVersion;
    private Long fileScopeVersion;
    private String fileSha256;
    @TableField(value = "ownership_confirmed_flag")
    private Boolean ownershipConfirmed;
    private String statusCode;
    private Integer currentMarker;
    /** 方案页统一填写的计划割接时间。 */
    private LocalDateTime scheduledTime;
    @TableField(value = "submit_user_id")
    private Long submittedBy;
    @TableField(value = "submit_time")
    private LocalDateTime submittedAt;
    private Long approvalInstanceId;
    private Long approvalVersion;
    private Long sourcePlanRevisionId;
    private String revisionReasonCode;
    @TableField(value = "invalidate_user_id")
    private Long invalidatedBy;
    @TableField(value = "invalidate_time")
    private LocalDateTime invalidatedAt;
    private String invalidationReasonCode;


    /** 提交来源任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceTaskVersion;
    /** 来源项目ID；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceProjectId;
    /** 来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceProjectVersion;
    /** 来源项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceProjectScopeVersion;
    /** 来源割接类型，决定模板章节适用性；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String sourceCutoverTypeCode;
    /** 填写值：项目情况/方案概述 */
    private String projectDescription;
    /** 填写值：回退方案说明 */
    private String rollbackDescription;
    /** CUS客户联系人公开引用 */
    private Long customerContactId;
    /** 联系人事实版本 */
    private Long customerContactVersion;
    /** 完整上传文件的工件事实水位 */
    private Long fileArtifactFactVersion;
    /** 完整上传文件的引用事实水位 */
    private Long fileReferenceFactVersion;
    /** 完整上传文件的可用性水位 */
    private Long fileAvailabilityFactVersion;
}
