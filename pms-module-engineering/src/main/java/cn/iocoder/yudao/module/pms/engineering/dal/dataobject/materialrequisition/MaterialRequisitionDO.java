package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PMS OA 领料申请 DO（FR-ENG-002）。
 * <p>
 * 对应表 {@code pms_eng_material_requisition}。
 * 状态：0 草稿、1 已提交、2 审批中、3 已通过、4 已驳回、5 已撤回、6 已终止。
 */
@TableName("pms_eng_material_requisition")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialRequisitionDO extends TenantBaseDO {

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
     * 领料单号，全局唯一
     */
    private String code;
    /**
     * 领料名称
     */
    private String name;
    /**
     * 领料类型：SPARE 备件 / CONSUMABLE 耗材 / TOOL 工具 / OTHER 其他
     */
    private String requisitionType;
    /**
     * 关联设备编号
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
     * 规格型号
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
     * 需求日期
     */
    private LocalDate neededDate;
    /**
     * 仓库编号
     */
    private Long warehouseId;
    /**
     * 仓库名称
     */
    private String warehouseName;
    /**
     * 库存状态：IN_STOCK 有库存 / OUT_OF_STOCK 无库存 / RESERVED 已预留
     */
    private String stockStatus;
    /**
     * 附件文件
     */
    private String attachmentFiles;
    /**
     * 触发来源：MANUAL 手动 / WBS 任务触发 / ISSUE 问题触发
     */
    private String triggerSource;
    /**
     * 触发来源关联编号
     */
    private Long triggerRefId;
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
     * BPM 流程实例编号
     */
    private String bpmProcessInstanceId;
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
