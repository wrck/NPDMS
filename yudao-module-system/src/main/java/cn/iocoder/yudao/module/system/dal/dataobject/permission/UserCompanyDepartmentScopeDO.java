package cn.iocoder.yudao.module.system.dal.dataobject.permission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户在同一条记录中的公司、部门有效范围。
 */
@TableName("system_user_company_department_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserCompanyDepartmentScopeDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long userId;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private String scopeRole;
    private Boolean isPrimary;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer status;
    private Integer version;

}
