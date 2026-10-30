package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_node")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalNodeDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Integer nodeNo;
    private String nodeCode;
    private String statusCode;
    private Long originalApproverUserId;
    private Long currentApproverUserId;
    @TableField(exist = false)
    private String candidateFactSnapshot;
    private Long projectScopeVersion;
    private String assessmentReviewDecisionCode;
    private String assessmentReviewReason;
    private String feedback;
    @TableField(value = "decide_time")
    private LocalDateTime decisionAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer pendingMarker;
    @Version
    private Integer version;


    /** ROLE或PERSONAL技术办理模式，不新增业务审批节点；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String approvalModeCode;
    /** SYSTEM实际角色ID */
    private Long candidateRoleId;
    /** SYSTEM实际角色编码 */
    private String candidateRoleCode;
    /** BPM原生候选组键 */
    private String candidateGroupKey;
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
    /** 真正完成本节点的办理人 */
    private Long decisionActorUserId;
    /** 办理时实际角色ID */
    private Long decisionRoleId;
    /** 办理时单人成员关系水位 */
    private Long decisionMembershipVersion;
    /** 办理时用户有效性水位 */
    private Long decisionUserStatusVersion;
    /** 决定事务资格核验时间 */
    private LocalDateTime decisionCheckTime;
}
