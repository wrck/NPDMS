package cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 业务联调 Response VO（FR-ENG-024）。
 */
@Schema(description = "管理后台 - 业务联调 Response VO")
@Data
public class JointTestRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "联调编码", example = "JT-2026-001")
    private String code;

    @Schema(description = "联调用例", example = "核心交换机连通性测试")
    private String testCase;

    @Schema(description = "关联设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "参与方")
    private String participants;

    @Schema(description = "联调时间")
    private LocalDateTime testTime;

    @Schema(description = "联调人", example = "1")
    private Long testerUserId;

    @Schema(description = "联调结果")
    private String result;

    @Schema(description = "异常记录")
    private String exceptionRecord;

    @Schema(description = "证据附件")
    private String evidenceUrl;

    @Schema(description = "状态：0 待联调 1 进行中 2 通过 3 失败", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
