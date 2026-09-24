package cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品信息分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductOfficialPageReqVO extends PageParam {

    @Schema(description = "关键字：跨产品名称/编码/型号模糊", example = "IPS2000")
    private String keyword;
}
