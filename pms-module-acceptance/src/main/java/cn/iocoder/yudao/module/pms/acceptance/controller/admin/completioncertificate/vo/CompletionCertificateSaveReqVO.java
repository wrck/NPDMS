package cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 电子完工证明创建/修改 Request VO")
@Data
public class CompletionCertificateSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "完工证明名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目完工证明")
    @NotBlank(message = "完工证明名称不能为空")
    @Size(max = 128, message = "完工证明名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "证明编号（业务编号）", example = "CERT-2026-001")
    @Size(max = 64, message = "证明编号长度不能超过 64 个字符")
    private String certificateNo;

    @Schema(description = "客户编号", example = "200")
    private Long customerId;

    @Schema(description = "完工日期")
    private LocalDate completionDate;

    @Schema(description = "工程服务类型：工程实施/工程督导", example = "工程实施")
    @Size(max = 32, message = "工程服务类型长度不能超过 32 个字符")
    private String serviceType;

    @Schema(description = "迪普工程师用户编号", example = "100")
    private Long engineerUserId;

    @Schema(description = "迪普工程师姓名（落证时快照）", example = "张三")
    @Size(max = 64, message = "迪普工程师姓名长度不能超过 64 个字符")
    private String engineerName;

    @Schema(description = "迪普工程师联系方式", example = "13800000000")
    @Size(max = 64, message = "迪普工程师联系方式长度不能超过 64 个字符")
    private String engineerContact;

    @Schema(description = "客户单位", example = "XX电力有限公司")
    @Size(max = 255, message = "客户单位长度不能超过 255 个字符")
    private String customerUnit;

    @Schema(description = "合同号", example = "HT-2026-001")
    @Size(max = 64, message = "合同号长度不能超过 64 个字符")
    private String contractNo;

    @Schema(description = "工程服务内容① 完成到货验收：是/否/不涉及", example = "是")
    @Size(max = 16, message = "工程服务内容① 长度不能超过 16 个字符")
    private String itemArrival;

    @Schema(description = "工程服务内容② 完成设备硬件安装和软件调测：是/否/不涉及", example = "是")
    @Size(max = 16, message = "工程服务内容② 长度不能超过 16 个字符")
    private String itemInstall;

    @Schema(description = "工程服务内容③ 完成业务上线/割接且业务测试正常：是/否/不涉及", example = "是")
    @Size(max = 16, message = "工程服务内容③ 长度不能超过 16 个字符")
    private String itemCutover;

    @Schema(description = "工程服务内容④ 完成产品维护现场讲解和培训：是/否/不涉及", example = "是")
    @Size(max = 16, message = "工程服务内容④ 长度不能超过 16 个字符")
    private String itemTraining;

    @Schema(description = "工程服务内容⑤ 工程文档、帐号密码已移交并协助修改：是/否/不涉及", example = "是")
    @Size(max = 16, message = "工程服务内容⑤ 长度不能超过 16 个字符")
    private String itemDocs;

    @Schema(description = "甲方签章图片地址")
    @Size(max = 500, message = "甲方签章图片地址长度不能超过 500 个字符")
    private String customerSignUrl;

    @Schema(description = "甲方签章日期")
    private LocalDate customerSignDate;

    @Schema(description = "服务方签章图片地址")
    @Size(max = 500, message = "服务方签章图片地址长度不能超过 500 个字符")
    private String vendorSignUrl;

    @Schema(description = "服务方签章日期")
    private LocalDate vendorSignDate;

    @Schema(description = "设备明细子表")
    @Valid
    private List<CompletionCertificateDeviceSaveReqVO> devices;

    @Schema(description = "完工证明内容")
    private String content;

    @Schema(description = "附件地址")
    @Size(max = 500, message = "附件地址长度不能超过 500 个字符")
    private String attachmentUrl;

    @Schema(description = "状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回", example = "0")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
