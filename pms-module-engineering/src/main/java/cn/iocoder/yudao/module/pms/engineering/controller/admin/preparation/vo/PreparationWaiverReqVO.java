package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PreparationWaiverReqVO {
    @NotNull private Integer expectedInputVersion;
    @NotNull private Integer expectedReadinessVersion;
    @NotNull private Integer expectedItemVersion;
    private Integer expectedWaiverVersion;
    @NotNull private Integer expectedProjectVersion;
    private List<String> blockerCodes;
    private String reason;
    private String risk;
    private String compensation;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String opinion;
}
