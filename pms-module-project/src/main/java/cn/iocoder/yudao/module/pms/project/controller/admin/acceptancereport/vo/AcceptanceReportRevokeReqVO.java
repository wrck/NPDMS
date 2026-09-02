package cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AcceptanceReportRevokeReqVO {

    @NotNull @Positive
    private Long expectedCurrentReportVersionId;
    @NotNull @Positive
    private Integer expectedCurrentReportVersionNo;
}
