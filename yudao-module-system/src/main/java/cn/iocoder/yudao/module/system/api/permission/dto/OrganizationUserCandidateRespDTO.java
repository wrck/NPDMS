package cn.iocoder.yudao.module.system.api.permission.dto;

import lombok.Data;

@Data
public class OrganizationUserCandidateRespDTO {

    private Long userId;
    private String username;
    private String nickname;
    private String employeeNo;
    private Long companyId;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;

}
