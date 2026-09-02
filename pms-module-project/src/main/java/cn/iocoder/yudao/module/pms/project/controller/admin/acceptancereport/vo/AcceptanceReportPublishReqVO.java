package cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AcceptanceReportPublishReqVO {

    @NotNull @Positive
    private Integer expectedReportVersionNo;
    private Long expectedCurrentReportVersionId;
}
