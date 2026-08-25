package cn.iocoder.yudao.module.system.controller.admin.company.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyRespVO {

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;

}
