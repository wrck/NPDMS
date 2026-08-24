package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台 - 版本化项目树查询 Response VO")
public class ProjectTreeQueryRespVO {
    private Long treeVersion;
    private List<ProjectRespVO> items;
    private String nextCursor;
    private Boolean updating;
}
