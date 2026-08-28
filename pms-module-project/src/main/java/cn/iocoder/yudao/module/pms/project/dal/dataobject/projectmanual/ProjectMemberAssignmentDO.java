package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目成员角色区间 DO（F-PM01 / V57 `proj_project_member_assignment`）
 * <p>
 * 成员角色的时态区间留痕：`effective_to IS NULL`=当前有效；
 * 同项目同用户同角色区间不得重叠（应用层防重，uk 含 effective_from）。
 * 指派语义=关闭旧区间+开启新区间（历史区间保留）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_member_assignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectMemberAssignmentDO extends TenantBaseDO {

    /**
     * 成员区间ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 成员工号
     */
    private String employeeNo;
    /**
     * 成员姓名
     */
    private String memberName;
    /**
     * 成员加入时公司主档ID
     */
    private Long companyId;
    /**
     * 公司编码
     */
    private String companyCode;
    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 部门ID快照
     */
    private Long departmentId;
    /**
     * 部门编码
     */
    private String departmentCode;
    /**
     * 部门名称
     */
    private String departmentName;
    /**
     * 成员角色（字典 pms_project_member_role：PROJECT_MANAGER/SERVICE_MANAGER_L1/SERVICE_MANAGER_L2）
     */
    private String memberRole;
    /**
     * 责任类型（PRIMARY/COLLABORATOR）
     */
    private String assignmentType;
    /**
     * AST站点稳定ID；L1统筹责任可为空
     */
    private Long siteId;
    /**
     * 职责
     */
    private String responsibility;
    /**
     * 指派或改派原因
     */
    private String changeReason;
    /**
     * 生效开始时间
     */
    private LocalDateTime effectiveFrom;
    /**
     * 失效时间（NULL=当前有效）
     */
    private LocalDateTime effectiveTo;
    /**
     * 状态（记录状态 ACTIVE；区间有效性由 effective_to 表达）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
