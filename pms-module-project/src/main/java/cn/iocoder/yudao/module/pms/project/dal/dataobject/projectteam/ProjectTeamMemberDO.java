package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目团队成员 DO
 */
@TableName("proj_project_member_assignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTeamMemberDO extends TenantBaseDO {

    /**
     * 团队成员编号
     */
    @TableId
    private Long id;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 用户编号
     */
    private Long userId;
    /**
     * 角色编码，如 PROJECT_MANAGER/SERVICE_MANAGER/ENGINEER
     */
    @TableField("member_role")
    private String roleCode;
    /**
     * 角色名称
     */
    private String roleName;
    /**
     * 状态：0启用 1停用
     */
    private Integer status;
    /**
     * 备注
     */
    @TableField("responsibility")
    private String remark;

}
