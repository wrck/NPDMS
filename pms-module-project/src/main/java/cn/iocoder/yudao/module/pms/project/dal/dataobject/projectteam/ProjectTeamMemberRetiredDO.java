package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目团队成员 DO
  * @deprecated 已随 pms_* 旧域退役：对应表已更名 pms_project_team_member_retired 且数据库侧仅允许查询（INSERT/UPDATE/DELETE 被触发器拒绝）；仅保留存量只读兼容，禁止新代码引用。
*/
@TableName("pms_project_team_member_retired")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class ProjectTeamMemberRetiredDO extends TenantBaseDO {

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
    private String remark;

}
