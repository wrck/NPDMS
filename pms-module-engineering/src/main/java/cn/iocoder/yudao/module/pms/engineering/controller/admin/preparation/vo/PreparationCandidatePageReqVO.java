package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PreparationCandidatePageReqVO extends PageParam {
    @Size(max = 64) private String keyword;
}
