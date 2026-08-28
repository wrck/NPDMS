package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目 Response VO（列表行与详情共用：身份/编码命名空间/四维/模板绑定）
 */
@Schema(description = "管理后台 - 项目 Response VO")
@Data
public class ProjectRespVO {

    @Schema(description = "项目ID")
    private Long id;

    @Schema(description = "项目编码（租户内唯一，创建后不可变）")
    private String projectCode;

    @Schema(description = "编码命名空间根项目ID（根项目=自身ID）")
    private Long codeRootId;

    @Schema(description = "编码命名空间内流水号（根项目=0，子项目>0 属 PM-02）")
    private Integer projectSequence;

    @Schema(description = "编码规则版本（创建时冻结）")
    private String codeRuleVersion;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "父项目ID（NULL=根项目）")
    private Long parentId;

    @Schema(description = "客户编码")
    private String customerCode;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "负责人姓名")
    private String managerName;

    @Schema(description = "下单公司稳定ID")
    private Long companyId;

    @Schema(description = "下单公司编码")
    private String companyCode;

    @Schema(description = "下单公司名称")
    private String companyName;

    @Schema(description = "下单办事处部门稳定ID")
    private Long departmentId;

    @Schema(description = "下单办事处部门编码")
    private String departmentCode;

    @Schema(description = "下单办事处部门名称")
    private String departmentName;

    @Schema(description = "签约方式")
    private String signingMethod;

    @Schema(description = "项目类别")
    private String projectCategory;

    @Schema(description = "实施方式")
    private String implementationMode;

    @Schema(description = "重大项目级别（NULL=不限）")
    private String majorProjectLevel;

    @Schema(description = "手工登记合同号")
    private String contractNo;

    @Schema(description = "实施地点")
    private String implementationLocation;

    @Schema(description = "地点解析状态：RESOLVED/UNRESOLVED")
    private String locationResolutionStatus;

    @Schema(description = "手工创建原因")
    private String creationReason;

    @Schema(description = "冻结的生命周期模板ID")
    private Long lifecycleTemplateId;

    @Schema(description = "冻结的模板版本号")
    private Integer lifecycleTemplateRevisionNo;

    @Schema(description = "模板加载方式：AUTO_DEFAULT/MANUAL_SELECTED")
    private String templateLoadMethod;

    @Schema(description = "冻结的流程定义引用")
    private String processDefinitionKey;

    @Schema(description = "冻结的流程定义版本")
    private String processDefinitionVersion;

    @Schema(description = "创建来源：MANUAL/ORDER/MIGRATION")
    private String sourceType;

    @Schema(description = "项目状态（S0~S6/MAINT）")
    private String status;

    @Schema(description = "生命周期状态")
    private String lifecycleStatus;

    @Schema(description = "当前阶段")
    private String currentStage;

    @Schema(description = "主责指派状态")
    private String assignmentStatus;

    @Schema(description = "Project乐观锁版本")
    private Integer version;

    @Schema(description = "项目开始时间")
    private LocalDateTime projectStartTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
