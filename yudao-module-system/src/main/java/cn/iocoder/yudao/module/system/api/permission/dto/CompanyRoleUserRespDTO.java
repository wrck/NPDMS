package cn.iocoder.yudao.module.system.api.permission.dto;

import lombok.Data;

/** PM-01 / INT-09：资格查询最小投影；不是项目成员或新授权事实。 */
@Data
public class CompanyRoleUserRespDTO {
    private Long userId;
    private String username;
    private String nickname;
    private Long companyId;
    private String roleCode;
}
