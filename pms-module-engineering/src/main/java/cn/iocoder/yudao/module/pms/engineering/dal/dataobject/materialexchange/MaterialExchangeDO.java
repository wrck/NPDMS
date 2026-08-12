package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PMS 物料换货协同 DO（FR-ENG-003）。
 * <p>
 * 对应表 {@code pms_eng_material_exchange}。
 * 单据状态：0 草稿、1 已提交、2 审批中、3 已通过、4 已驳回、5 已撤回、6 已终止。
 * CRM 推送状态：PENDING 待推送 / SENT 已推送 / RECEIVED 已接收。
 */
@TableName("pms_eng_material_exchange")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialExchangeDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 换货单号，全局唯一
     */
    private String code;
    /**
     * 换货名称
     */
    private String name;
    /**
     * 换货类型：INCOMPATIBLE 不兼容 / DEFECTIVE 缺陷 / DAMAGED 损坏 / OTHER 其他
     */
    private String exchangeType;
    /**
     * 原设备编号
     */
    private Long equipmentId;
    /**
     * 物料名称
     */
    private String materialName;
    /**
     * 物料编码
     */
    private String materialCode;
    /**
     * 规格型号描述
     */
    private String specification;
    /**
     * 数量
     */
    private BigDecimal quantity;
    /**
     * 单位，默认 个
     */
    private String unit;
    /**
     * 原订单号
     */
    private String originalOrderNo;
    /**
     * 换货原因
     */
    private String reason;
    /**
     * 原因附件文件
     */
    private String reasonFiles;
    /**
     * CRM 推送状态：PENDING 待推送 / SENT 已推送 / RECEIVED 已接收
     */
    private String crmPushStatus;
    /**
     * CRM 推送时间
     */
    private LocalDateTime crmPushTime;
    /**
     * CRM 工单号
     */
    private String crmOrderNo;
    /**
     * 新设备编号（换货后设备）
     */
    private Long newEquipmentId;
    /**
     * 换货进度描述
     */
    private String exchangeProgress;
    /**
     * 申请人编号
     */
    private Long applicantUserId;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;
    /**
     * 审批人编号
     */
    private Long approverUserId;
    /**
     * 审批时间
     */
    private LocalDateTime approveTime;
    /**
     * 审批意见
     */
    private String approveOpinion;
    /**
     * 审批动作：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签
     */
    private String approveAction;
    /**
     * 状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
