package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreparationItemPatchRespVO {
    private Long preparationId;
    private Long itemId;
    private Integer preparationVersion;
    private Integer inputVersion;
    private Integer itemVersion;
    private Integer formVersion;
}
