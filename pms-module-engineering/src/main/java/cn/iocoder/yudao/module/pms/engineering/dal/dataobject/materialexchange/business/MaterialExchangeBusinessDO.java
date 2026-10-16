package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@TableName("imp_eng_material_exchange") @Data @EqualsAndHashCode(callSuper=true)
@ProjectBusinessModel(ownerModule="IMP",entityType="materialExchange",stableCode="IMP_MATERIAL_EXCHANGE",name="设备换货",permissionPrefix="pms:imp-material-exch",nativeEntityType="MATERIAL_EXCHANGE")
public class MaterialExchangeBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="换货单号", displayOrder=20)
    @jakarta.validation.constraints.NotBlank(message = "换货单号不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "换货单号长度不能超过 64 个字符")
    private String code;
    @BusinessModelField(name="换货名称", displayOrder=30)
    @jakarta.validation.constraints.NotBlank(message = "换货名称不能为空")
    @jakarta.validation.constraints.Size(max = 200, message = "换货名称长度不能超过 200 个字符")
    private String name;
    @BusinessModelField(name="换货类型", displayOrder=40, writable=false)
    @jakarta.validation.constraints.Size(max = 32, message = "换货类型长度不能超过 32 个字符")
    private String exchangeType;
    @BusinessModelField(name="原设备编号（设备表引用）", displayOrder=50, writable=false)
    private Long deviceId;
    @BusinessModelField(name="产品名称", displayOrder=60, writable=false)
    @jakarta.validation.constraints.Size(max = 200, message = "产品名称长度不能超过 200 个字符")
    private String productName;
    @BusinessModelField(name="产品编码（订单行去重拼接）", displayOrder=70, writable=false)
    @jakarta.validation.constraints.Size(max = 500, message = "产品编码长度不能超过 500 个字符")
    private String productCode;
    @BusinessModelField(name="产品型号", displayOrder=80, writable=false)
    @jakarta.validation.constraints.Size(max = 200, message = "产品型号长度不能超过 200 个字符")
    private String productModel;
    @BusinessModelField(name="数量", displayOrder=90, writable=false)
    @jakarta.validation.constraints.NotNull(message = "数量不能为空")
    private BigDecimal quantity;
    @BusinessModelField(name="单位", displayOrder=100)
    @jakarta.validation.constraints.Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;
    @BusinessModelField(name="原订单号", displayOrder=110, writable=false)
    @jakarta.validation.constraints.Size(max = 64, message = "原订单号长度不能超过 64 个字符")
    private String originalOrderNo;
    @BusinessModelField(name="换货原因", displayOrder=120)
    @jakarta.validation.constraints.NotBlank(message = "换货原因不能为空")
    private String reason;
    @BusinessModelField(name="原因附件文件", displayOrder=130, writable=false)
    @jakarta.validation.constraints.Size(max = 2000, message = "原因附件文件长度不能超过 2000 个字符")
    private String reasonFiles;
    @BusinessModelField(name="CRM 推送状态", displayOrder=140, writable=false)
    private String crmPushStatus;
    @BusinessModelField(name="CRM 推送时间", displayOrder=150, writable=false)
    private LocalDateTime crmPushTime;
    @BusinessModelField(name="CRM 工单号", displayOrder=160, writable=false)
    private String crmOrderNo;
    @BusinessModelField(name="换货后设备", displayOrder=170, writable=false)
    private Long newDeviceId;
    @BusinessModelField(name="换货进度描述", displayOrder=180, writable=false)
    private String exchangeProgress;
    @BusinessModelField(name="申请人编号", displayOrder=190, writable=false)
    private Long applicantUserId;
    @BusinessModelField(name="申请时间", displayOrder=200, writable=false)
    private LocalDateTime applyTime;
    @BusinessModelField(name="审批人编号", displayOrder=210, writable=false)
    private Long approverUserId;
    @BusinessModelField(name="审批时间", displayOrder=220, writable=false)
    private LocalDateTime approveTime;
    @BusinessModelField(name="审批意见", displayOrder=230, writable=false)
    private String approveOpinion;
    @BusinessModelField(name="审批动作", displayOrder=240, writable=false)
    private String approveAction;
    @BusinessModelField(name="状态", displayOrder=250, writable=false)
    private Integer status;
    @BusinessModelField(name="备注", displayOrder=260)
    @jakarta.validation.constraints.Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
    @TableField(exist=false)
    @BusinessModelField(name="设备及物料明细", displayOrder=400, writable=false, searchable=false, sortable=false)
    private java.util.List<cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSerialVO> serials;
    @TableField(exist=false) private String pendingSerialsJson;
}
