package cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 初验/终验分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptancePageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "验收编码，模糊匹配", example = "ACC-001")
    private String code;

    @Schema(description = "验收名称，模糊匹配", example = "初验")
    private String name;

    @Schema(description = "验收类型 PRELIMINARY 初验 / FINAL 终验", example = "PRELIMINARY")
    private String acceptanceType;

    @Schema(description = "状态 0草稿 1待提交 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

    @Schema(description = "验收日期范围")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] acceptanceDate;

}
