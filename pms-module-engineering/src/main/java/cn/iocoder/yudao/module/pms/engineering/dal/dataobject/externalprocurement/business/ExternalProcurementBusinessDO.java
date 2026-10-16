package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@TableName("imp_eng_external_procurement") @Data @EqualsAndHashCode(callSuper=true)
@ProjectBusinessModel(ownerModule="IMP",entityType="externalProcurement",stableCode="IMP_EXTERNAL_PROCUREMENT",name="外部采购",permissionPrefix="pms:imp-ext-proc",nativeEntityType="EXTERNAL_PROCUREMENT")
public class ExternalProcurementBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="外采单号", displayOrder=20)
    @jakarta.validation.constraints.NotBlank(message = "外采单号不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "外采单号长度不能超过 64 个字符")
    private String code;
    @BusinessModelField(name="外采名称", displayOrder=30)
    @jakarta.validation.constraints.NotBlank(message = "外采名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "外采名称长度不能超过 200 个字符")
    private String name;
    @BusinessModelField(name="外采类型", displayOrder=40)
    @jakarta.validation.constraints.Size(max = 32, message = "外采类型长度不能超过 32 个字符")
    private String procurementType;
    @BusinessModelField(name="产品名称/物料名称", displayOrder=50)
    @jakarta.validation.constraints.NotBlank(message = "产品名称/物料名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "产品名称/物料名称长度不能超过 200 个字符")
    private String productName;
    @BusinessModelField(name="产品编码/物料编码", displayOrder=60)
    @jakarta.validation.constraints.Size(max = 64, message = "产品编码/物料编码长度不能超过 64 个字符")
    private String productCode;
    @BusinessModelField(name="规格型号描述", displayOrder=70)
    @jakarta.validation.constraints.Size(max = 200, message = "规格长度不能超过 200 个字符")
    private String specification;
    @BusinessModelField(name="品牌", displayOrder=80)
    @jakarta.validation.constraints.Size(max = 100, message = "品牌长度不能超过 100 个字符")
    private String brand;
    @BusinessModelField(name="产品型号/物料型号", displayOrder=90)
    @jakarta.validation.constraints.Size(max = 100, message = "产品型号/物料型号长度不能超过 100 个字符")
    private String productModel;
    @BusinessModelField(name="数量", displayOrder=100)
    @jakarta.validation.constraints.NotNull(message = "数量不能为空")
    private BigDecimal quantity;
    @BusinessModelField(name="单位", displayOrder=110)
    @jakarta.validation.constraints.Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;
    @BusinessModelField(name="单价", displayOrder=120)
    private BigDecimal unitPrice;
    @BusinessModelField(name="总价", displayOrder=130)
    private BigDecimal totalPrice;
    @BusinessModelField(name="币种", displayOrder=140)
    @jakarta.validation.constraints.Size(max = 8, message = "币种长度不能超过 8 个字符")
    private String currency;
    @BusinessModelField(name="供应商名称", displayOrder=150)
    @jakarta.validation.constraints.Size(max = 200, message = "供应商名称长度不能超过 200 个字符")
    private String supplierName;
    @BusinessModelField(name="供应商联系人", displayOrder=160)
    @jakarta.validation.constraints.Size(max = 100, message = "供应商联系人长度不能超过 100 个字符")
    private String supplierContact;
    @BusinessModelField(name="供应商联系电话", displayOrder=170)
    @jakarta.validation.constraints.Size(max = 64, message = "供应商联系电话长度不能超过 64 个字符")
    private String supplierPhone;
    @BusinessModelField(name="需求日期", displayOrder=180)
    private LocalDate neededDate;
    @BusinessModelField(name="期望交付日期", displayOrder=190)
    private LocalDate expectedDeliveryDate;
    @BusinessModelField(name="附件文件", displayOrder=200, writable=false)
    @jakarta.validation.constraints.Size(max = 2000, message = "附件文件长度不能超过 2000 个字符")
    private String attachmentFiles;
    @BusinessModelField(name="触发来源", displayOrder=210)
    @jakarta.validation.constraints.Size(max = 32, message = "触发来源长度不能超过 32 个字符")
    private String triggerSource;
    @BusinessModelField(name="触发来源关联编号", displayOrder=220)
    private Long triggerRefId;
    @BusinessModelField(name="申请人编号", displayOrder=230)
    private Long applicantUserId;
    @BusinessModelField(name="申请时间", displayOrder=240)
    private LocalDateTime applyTime;
    @BusinessModelField(name="审批人编号", displayOrder=250, writable=false)
    private Long approverUserId;
    @BusinessModelField(name="审批时间", displayOrder=260, writable=false)
    private LocalDateTime approveTime;
    @BusinessModelField(name="审批意见", displayOrder=270, writable=false)
    private String approveOpinion;
    @BusinessModelField(name="审批动作", displayOrder=280, writable=false)
    private String approveAction;
    @BusinessModelField(name="BPM 流程实例编号", displayOrder=290, writable=false)
    private String bpmProcessInstanceId;
    @BusinessModelField(name="状态", displayOrder=300, writable=false)
    private Integer status;
    @BusinessModelField(name="备注", displayOrder=310)
    @jakarta.validation.constraints.Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
