package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PreparationFormRespVO {
    private Long formInstanceId;
    private String formCode;
    private Integer formVersion;
    private String schemaSnapshot;
    private String valueSnapshot;
    private String status;
    private LocalDateTime frozenAt;
    private Long frozenBy;
    private Integer version;
}
