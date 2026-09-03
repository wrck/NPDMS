package cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AcceptanceReportDraftReqVO {

    @Positive
    private Integer expectedReportVersionNo;
    private LocalDateTime acceptanceTime;
    @Size(max = 32)
    private String conclusionCode;
    @Size(max = 2000)
    private String conclusionText;
    @Size(max = 128)
    private String acceptorName;
}
