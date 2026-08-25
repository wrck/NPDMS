package cn.iocoder.yudao.module.system.api.permission.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCompanyDepartmentScopeRespDTO {

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
