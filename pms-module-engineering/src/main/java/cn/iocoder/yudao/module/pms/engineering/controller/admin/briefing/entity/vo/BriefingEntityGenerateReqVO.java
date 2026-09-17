package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 复制旧生成参数；真实文档生成能力不在本次实体迁移中补造。 */
@Data
public class BriefingEntityGenerateReqVO {
    @NotNull
    private Long id;
    private Long templateId;
    private String sourceSnapshot;
    private Integer version;
}
