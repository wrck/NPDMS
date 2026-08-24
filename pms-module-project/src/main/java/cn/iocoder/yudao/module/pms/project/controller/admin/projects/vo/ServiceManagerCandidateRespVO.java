package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 服务经理候选 Response VO")
@Data
public class ServiceManagerCandidateRespVO {

    private Long userId;
    private String username;
    private String nickname;
    private String employeeNo;
    private Long companyId;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
}
