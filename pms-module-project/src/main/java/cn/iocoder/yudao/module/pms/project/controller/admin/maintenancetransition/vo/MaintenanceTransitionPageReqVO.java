package cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "管理后台 - 转维保分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaintenanceTransitionPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "转维保编码，模糊匹配", example = "MT-001")
    private String code;

    @Schema(description = "转维保名称，模糊匹配", example = "维保")
    private String name;

    @Schema(description = "设备编号", example = "200")
    private Long equipmentId;

    @Schema(description = "状态 0草稿 1待生效 2生效中 3已过期 4已续保", example = "0")
    private Integer status;

    @Schema(description = "维保开始日期范围")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] startDate;

}
