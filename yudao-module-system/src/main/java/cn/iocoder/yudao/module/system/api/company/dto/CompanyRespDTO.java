package cn.iocoder.yudao.module.system.api.company.dto;

import lombok.Data;

@Data
public class CompanyRespDTO {

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Integer version;

}
