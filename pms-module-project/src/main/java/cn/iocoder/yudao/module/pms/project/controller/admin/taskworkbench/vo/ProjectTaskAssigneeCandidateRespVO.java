package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 项目任务负责人候选 Response VO")
@Data
public class ProjectTaskAssigneeCandidateRespVO {

    private Long userId;
    private String username;
    private String nickname;
    private String employeeNo;
    private Long companyId;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
}
