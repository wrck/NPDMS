package cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 离线巡检文件创建/修改 Request VO")
@Data
public class SrvOfflineFileSaveReqVO {

    @Schema(description = "文件编号", example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属巡检任务编号不能为空")
    private Long taskId;

    @Schema(description = "文件编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "F-001")
    @NotBlank(message = "文件编码不能为空")
    @Size(max = 64, message = "文件编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "文件存储地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "/pms/files/xxx.log")
    @NotBlank(message = "文件存储地址不能为空")
    @Size(max = 500, message = "文件存储地址长度不能超过 500 个字符")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "1024")
    private Long fileSize;

    @Schema(description = "文件校验值", example = "md5:xxxx")
    private String fileChecksum;

    @Schema(description = "解析状态 0待解析 1解析中 2解析成功 3解析失败", example = "0")
    private Integer parseStatus;

    @Schema(description = "解析结果")
    private String parseResult;

    @Schema(description = "错误明细")
    private String errorDetail;

    @Schema(description = "解析人", example = "300")
    private Long parsedBy;

    @Schema(description = "解析时间")
    private LocalDateTime parsedTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
