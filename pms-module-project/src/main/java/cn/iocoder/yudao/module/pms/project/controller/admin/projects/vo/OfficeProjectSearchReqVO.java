package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OfficeProjectSearchReqVO extends PageParam {
    @Size(max = 512)
    private String projectName;
    @Size(max = 512)
    private String contractNo;

    @AssertTrue(message = "请至少填写完整项目名称或合同号")
    public boolean isExactCriterionPresent() {
        return projectName != null && !projectName.isBlank() || contractNo != null && !contractNo.isBlank();
    }
}
