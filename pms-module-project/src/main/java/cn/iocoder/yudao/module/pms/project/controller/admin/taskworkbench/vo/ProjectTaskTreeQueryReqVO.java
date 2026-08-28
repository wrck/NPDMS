package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectTaskTreeQueryReqVO {
    private String mode = "DIRECT_CHILDREN";
    private Long parentTaskId;
    private Long taskId;
    @Size(max = 64)
    private String businessLevelCode;
    @Size(max = 32)
    private String stageCode;
    @Size(max = 100)
    private String keyword;
    @Size(max = 100)
    private String cursor;
    @Min(1)
    @Max(200)
    private Integer pageSize = 50;
}
