package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import lombok.Data;

@Data
public class DurationChangeSubmitRespVO {

    private Long changeId;
    private String status;
    private String processInstanceId;
    private Integer changeVersion;
    private Integer planVersion;

}
