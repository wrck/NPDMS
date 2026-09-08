package cn.iocoder.yudao.module.system.api.permission.dto;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/** PM-01 / INT-09：指定公司、角色的上游有效用户查询，不限定部门。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyRoleUserPageReqDTO extends PageParam {

    @NotNull
    @Positive
    private Long companyId;

    @NotBlank
    @Size(max = 32)
    private String roleCode;

    /** null 不筛用户；空集合返回空；非空集合用于指定用户资格重验。 */
    private Set<@NotNull @Positive Long> userIds;

    @Size(max = 64)
    private String keyword;
}
