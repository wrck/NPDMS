package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_reassignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalReassignmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Long approvalNodeId;
    private Integer reassignmentNo;
    private Long fromApproverUserId;
    private Long toApproverUserId;
    private String reason;
    @TableField(exist = false)
    private String candidateFactSnapshot;
    @TableField(value = "operate_user_id")
    private Long operatedBy;
    @TableField(value = "operate_time")
    private LocalDateTime operatedAt;


    /** 单人资格来源种类：发起人项目权限或当前项目服务经理 */
    private String qualificationKindCode;
    /** 服务经理来源FOUND/NOT_UNIQUE等原结论 */
    private String qualificationOutcomeCode;
    /** 当时实际服务经理职责编码 */
    private String qualificationRoleCode;
    /** 当时项目版本 */
    private Long qualificationProjectVersion;
    /** 当时参与事实版本 */
    private Long qualificationParticipantFactVersion;
    /** 发起人需要的ACTION_EDIT等既有动作 */
    private String qualificationRequiredActionCode;
    /** 发起人当时项目授权结论 */
    private Boolean qualificationAllowedFlag;
    /** 资格事实核验时间 */
    private LocalDateTime qualificationCheckTime;
    /** 接收人当时项目范围版本 */
    private Long projectScopeVersion;
}
