package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目组织关系 DO（F-PM01 / V57 `proj_project_company_department_relation`）
 * <p>
 * V1 承载下单办事处（relation_role=ORDER_OFFICE，is_primary=1）。
 * 注意：生成列 `primary_project_id` 不映射（由数据库维护）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_company_department_relation")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectCompanyDepartmentRelationDO extends TenantBaseDO {

    /**
     * 组织关系ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 公司主档ID（未完成映射时可为空）
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
     * 部门主档ID（无部门维度时可为空）
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
     * 业务角色（字典 pms_company_relation_role；PM-01 使用 ORDER_OFFICE）
     */
    private String relationRole;
    /**
     * 同业务范围内是否主记录：0否/1是
     */
    private Boolean isPrimary;
    /**
     * 生效开始时间
     */
    private LocalDateTime effectiveFrom;
    /**
     * 失效时间（NULL=当前有效）
     */
    private LocalDateTime effectiveTo;
    /**
     * 状态
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
