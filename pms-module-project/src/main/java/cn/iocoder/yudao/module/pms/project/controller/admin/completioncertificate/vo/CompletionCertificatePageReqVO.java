package cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 电子完工证明分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompletionCertificatePageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "完工证明编码，模糊匹配", example = "CC-001")
    private String code;

    @Schema(description = "完工证明名称，模糊匹配", example = "完工")
    private String name;

    @Schema(description = "客户编号", example = "200")
    private Long customerId;

    @Schema(description = "状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回", example = "0")
    private Integer status;

    @Schema(description = "完工日期范围")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] completionDate;

}
