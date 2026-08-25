package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTaskTreeRespVO {
    private List<ProjectTaskNodeRespVO> rows;
    private String nextCursor;
    private Long taskTreeVersion;
    private String projectionWatermark;
}
