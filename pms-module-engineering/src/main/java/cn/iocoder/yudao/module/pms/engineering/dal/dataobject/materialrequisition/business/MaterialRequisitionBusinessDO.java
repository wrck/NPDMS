package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@TableName("imp_eng_material_requisition") @Data @EqualsAndHashCode(callSuper=true)
@ProjectBusinessModel(ownerModule="IMP",entityType="materialRequisition",stableCode="IMP_MATERIAL_REQUISITION",name="材料领用",permissionPrefix="pms:imp-material-req",nativeEntityType="MATERIAL_REQUISITION")
public class MaterialRequisitionBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="领料单号", displayOrder=20)
    @jakarta.validation.constraints.NotBlank(message = "领料单号不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "领料单号长度不能超过 64 个字符")
    private String code;
    @BusinessModelField(name="领料名称", displayOrder=30)
    @jakarta.validation.constraints.NotBlank(message = "领料名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "领料名称长度不能超过 200 个字符")
    private String name;
    @BusinessModelField(name="领料类型", displayOrder=40)
    @jakarta.validation.constraints.Size(max = 32, message = "领料类型长度不能超过 32 个字符")
    private String requisitionType;
    @BusinessModelField(name="关联设备编号", displayOrder=50)
    private Long equipmentId;
    @BusinessModelField(name="产品名称/物料名称", displayOrder=60)
    @jakarta.validation.constraints.NotBlank(message = "产品名称/物料名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "产品名称/物料名称长度不能超过 200 个字符")
    private String productName;
    @BusinessModelField(name="产品编码/物料编码", displayOrder=70)
    @jakarta.validation.constraints.Size(max = 64, message = "产品编码/物料编码长度不能超过 64 个字符")
    private String productCode;
    @BusinessModelField(name="规格型号", displayOrder=80)
    @jakarta.validation.constraints.Size(max = 200, message = "规格型号长度不能超过 200 个字符")
    private String specification;
    @BusinessModelField(name="数量", displayOrder=90)
    @jakarta.validation.constraints.NotNull(message = "数量不能为空")
    private BigDecimal quantity;
    @BusinessModelField(name="单位", displayOrder=100)
    @jakarta.validation.constraints.Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;
    @BusinessModelField(name="需求日期", displayOrder=110)
    private LocalDate neededDate;
    @BusinessModelField(name="仓库编号", displayOrder=120)
    private Long warehouseId;
    @BusinessModelField(name="仓库名称", displayOrder=130)
    @jakarta.validation.constraints.Size(max = 200, message = "仓库名称长度不能超过 200 个字符")
    private String warehouseName;
    @BusinessModelField(name="库存状态", displayOrder=140)
    @jakarta.validation.constraints.Size(max = 32, message = "库存状态长度不能超过 32 个字符")
    private String stockStatus;
    @BusinessModelField(name="附件文件", displayOrder=150, writable=false)
    @jakarta.validation.constraints.Size(max = 2000, message = "附件文件长度不能超过 2000 个字符")
    private String attachmentFiles;
    @BusinessModelField(name="触发来源", displayOrder=160)
    @jakarta.validation.constraints.Size(max = 32, message = "触发来源长度不能超过 32 个字符")
    private String triggerSource;
    @BusinessModelField(name="触发来源关联编号", displayOrder=170)
    private Long triggerRefId;
    @BusinessModelField(name="申请人编号", displayOrder=180)
    private Long applicantUserId;
    @BusinessModelField(name="申请时间", displayOrder=190)
    private LocalDateTime applyTime;
    @BusinessModelField(name="审批人编号", displayOrder=200, writable=false)
    private Long approverUserId;
    @BusinessModelField(name="审批时间", displayOrder=210, writable=false)
    private LocalDateTime approveTime;
    @BusinessModelField(name="审批意见", displayOrder=220, writable=false)
    private String approveOpinion;
    @BusinessModelField(name="审批动作", displayOrder=230, writable=false)
    private String approveAction;
    @BusinessModelField(name="BPM 流程实例编号", displayOrder=240, writable=false)
    private String bpmProcessInstanceId;
    @BusinessModelField(name="状态", displayOrder=250, writable=false)
    private Integer status;
    @BusinessModelField(name="备注", displayOrder=260)
    @jakarta.validation.constraints.Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
