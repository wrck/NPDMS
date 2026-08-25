package cn.iocoder.yudao.module.system.api.permission.dto;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationUserCandidatePageReqDTO extends PageParam {

    @NotNull(message = "公司ID不能为空")
    private Long companyId;

    @NotNull(message = "部门ID不能为空")
    private Long departmentId;

    @NotBlank(message = "部门编码不能为空")
    private String departmentCode;

    @Size(max = 64, message = "候选关键字长度不能超过64个字符")
    private String keyword;

}
