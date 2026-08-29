package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CUT-07配置修订分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationPageReqVO extends PageParam {
    private String configurationCode;
    private String configurationName;
    private String statusCode;
}
