package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerRespVO {

    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String customerLevel;
    private String lifecycleStatus;
    private String sourceType;
    private String sourceKey;
    private String sourceVersion;
    private String syncStatus;
    private LocalDateTime dataAsOf;
    private Boolean reconciliationPending;
    private String temporaryReason;
    private String contactPhone;
    private String contactEmail;
    private String departmentCode;
    private String departmentName;
    private String marketCode;
    private String marketName;
    private String systemCode;
    private String systemName;
    private String expendCode;
    private String expendName;
    private String industryCode;
    private String industryName;
    private String address;
    private String remark;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
