package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.module.pms.project.service.projecttree.command.ProjectTreeQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 版本化项目树查询 Request VO")
public class ProjectTreeQueryReqVO {
    @NotNull
    private ProjectTreeQuery.QueryType queryType = ProjectTreeQuery.QueryType.CHILDREN;
    private String businessLevelCode;
    @Min(1) @Max(500)
    private Integer pageSize = 100;
    private String cursor;
}
