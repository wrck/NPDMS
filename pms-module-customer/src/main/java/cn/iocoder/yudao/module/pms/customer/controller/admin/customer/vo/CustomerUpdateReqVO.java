package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class CustomerUpdateReqVO {

    private String name;
    private String shortName;
    private String remark;
    private String departmentCode;
    private String marketCode;
    private String systemCode;
    private String expendCode;
    private String industryCode;
    @NotEmpty
    private Set<String> changedFields;
}
