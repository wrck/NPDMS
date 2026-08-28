package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 项目节点服务经理责任分布 Response VO")
@Data
public class ServiceManagerResponsibilityRespVO {

    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long parentId;
    private Integer treeDepth;
    private String assignmentStatus;
    private List<ResponsibilityScope> responsibilities = new ArrayList<>();

    @Data
    public static class ResponsibilityScope {
        private String levelCode;
        private Long siteId;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private Manager primaryManager;
        private List<Manager> collaborators = new ArrayList<>();
    }

    @Data
    public static class Manager {
        private Long assignmentId;
        private Long userId;
        private String employeeNo;
        private String memberName;
        private LocalDateTime effectiveFrom;
        private String changeReason;
    }
}
