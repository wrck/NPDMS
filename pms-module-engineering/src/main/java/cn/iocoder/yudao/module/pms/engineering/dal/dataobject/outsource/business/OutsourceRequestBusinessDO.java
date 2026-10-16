package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@TableName("res_outsource_request") @Data @EqualsAndHashCode(callSuper=true)
@ProjectBusinessModel(ownerModule="RES",entityType="outsourceRequest",stableCode="RES_OUTSOURCE_REQUEST",name="外包申请",permissionPrefix="pms:res-outsource",nativeEntityType="OUTSOURCE_REQUEST")
public class OutsourceRequestBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="外包单号", displayOrder=20)
    @jakarta.validation.constraints.NotBlank(message = "外包单号不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "外包单号长度不能超过 64 个字符")
    private String code;
    @BusinessModelField(name="外包名称", displayOrder=30)
    @jakarta.validation.constraints.NotBlank(message = "外包名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "外包名称长度不能超过 200 个字符")
    private String name;
    @BusinessModelField(name="外包类型", displayOrder=40)
    @jakarta.validation.constraints.Size(max = 32, message = "外包类型长度不能超过 32 个字符")
    private String outsourceType;
    @BusinessModelField(name="工作内容", displayOrder=50)
    @jakarta.validation.constraints.NotBlank(message = "工作内容不能为空")
    private String workContent;
    @BusinessModelField(name="工作量", displayOrder=60)
    private BigDecimal workQuantity;
    @BusinessModelField(name="工作量单位", displayOrder=70)
    @jakarta.validation.constraints.Size(max = 32, message = "工作量单位长度不能超过 32 个字符")
    private String workUnit;
    @BusinessModelField(name="计划开始日期", displayOrder=80)
    private LocalDate plannedStartDate;
    @BusinessModelField(name="计划结束日期", displayOrder=90)
    private LocalDate plannedEndDate;
    @BusinessModelField(name="预估成本", displayOrder=100)
    private BigDecimal estimatedCost;
    @BusinessModelField(name="实际成本", displayOrder=110)
    private BigDecimal actualCost;
    @BusinessModelField(name="币种", displayOrder=120)
    @jakarta.validation.constraints.Size(max = 8, message = "币种长度不能超过 8 个字符")
    private String currency;
    @BusinessModelField(name="供应商编号", displayOrder=130)
    private Long vendorId;
    @BusinessModelField(name="供应商名称", displayOrder=140)
    @jakarta.validation.constraints.Size(max = 200, message = "供应商名称长度不能超过 200 个字符")
    private String vendorName;
    @BusinessModelField(name="联系人编号", displayOrder=150)
    private Long contactUserId;
    @BusinessModelField(name="联系电话", displayOrder=160)
    @jakarta.validation.constraints.Size(max = 64, message = "联系电话长度不能超过 64 个字符")
    private String contactPhone;
    @BusinessModelField(name="附件文件", displayOrder=170, writable=false)
    @jakarta.validation.constraints.Size(max = 2000, message = "附件文件长度不能超过 2000 个字符")
    private String attachmentFiles;
    @BusinessModelField(name="触发来源", displayOrder=180)
    @jakarta.validation.constraints.Size(max = 32, message = "触发来源长度不能超过 32 个字符")
    private String triggerSource;
    @BusinessModelField(name="触发来源关联编号", displayOrder=190)
    private Long triggerRefId;
    @BusinessModelField(name="申请人编号", displayOrder=200)
    private Long applicantUserId;
    @BusinessModelField(name="申请时间", displayOrder=210)
    private LocalDateTime applyTime;
    @BusinessModelField(name="审批人编号", displayOrder=220, writable=false)
    private Long approverUserId;
    @BusinessModelField(name="审批时间", displayOrder=230, writable=false)
    private LocalDateTime approveTime;
    @BusinessModelField(name="审批意见", displayOrder=240, writable=false)
    private String approveOpinion;
    @BusinessModelField(name="审批动作", displayOrder=250, writable=false)
    private String approveAction;
    @BusinessModelField(name="BPM 流程实例编号", displayOrder=260, writable=false)
    private String bpmProcessInstanceId;
    @BusinessModelField(name="状态", displayOrder=270, writable=false)
    private Integer status;
    @BusinessModelField(name="备注", displayOrder=280)
    @jakarta.validation.constraints.Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
    @TableField(exist=false) private String surveyExecutionJson;
}
