package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource;

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
 * PMS 外包申请 DO（FR-ENG-002）。
 * <p>
 * 对应表 {@code pms_eng_outsource_request}。
 * 状态：0 草稿、1 已提交、2 审批中、3 已通过、4 已驳回、5 已撤回、6 已终止。
 */
@TableName("pms_eng_outsource_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class OutsourceRequestDO extends TenantBaseDO {

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
     * 外包单号，全局唯一
     */
    private String code;
    /**
     * 外包名称
     */
    private String name;
    /**
     * 外包类型：LABOR 劳务 / SERVICE 服务 / OTHER 其他
     */
    private String outsourceType;
    /**
     * 工作内容
     */
    private String workContent;
    /**
     * 工作量
     */
    private BigDecimal workQuantity;
    /**
     * 工作量单位
     */
    private String workUnit;
    /**
     * 计划开始日期
     */
    private LocalDate plannedStartDate;
    /**
     * 计划结束日期
     */
    private LocalDate plannedEndDate;
    /**
     * 预估成本
     */
    private BigDecimal estimatedCost;
    /**
     * 实际成本
     */
    private BigDecimal actualCost;
    /**
     * 币种，默认 CNY
     */
    private String currency;
    /**
     * 供应商编号
     */
    private Long vendorId;
    /**
     * 供应商名称
     */
    private String vendorName;
    /**
     * 联系人编号
     */
    private Long contactUserId;
    /**
     * 联系电话
     */
    private String contactPhone;
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
