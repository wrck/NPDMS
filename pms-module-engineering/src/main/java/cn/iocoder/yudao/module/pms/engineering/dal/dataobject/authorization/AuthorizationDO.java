package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PMS 授权与借货 DO（FR-ENG-010）。
 * <p>
 * 对应表 {@code pms_eng_authorization}。
 * 状态：0 草稿、1 已提交、2 审批中、3 已通过、4 已驳回、5 已撤回、6 已终止。
 */
@TableName("pms_eng_authorization")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizationDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 授权编号（如 AUTH-2026-001），全局唯一
     */
    private String code;
    /**
     * 关联项目ID
     */
    private Long projectId;
    /**
     * 授权名称
     */
    private String name;
    /**
     * 授权类型：FORMAL 正式 / TEMPORARY 临时 / LOAN 借货
     */
    private String authorizationType;
    /**
     * 关联设备ID
     */
    private Long deviceId;
    /**
     * 设备序列号
     */
    private String deviceSerial;
    /**
     * 设备型号
     */
    private String deviceModel;
    /**
     * 授权密钥
     */
    private String licenseKey;
    /**
     * 授权类型描述
     */
    private String licenseType;
    /**
     * 申请开始日期
     */
    private LocalDate applyStartDate;
    /**
     * 申请结束日期
     */
    private LocalDate applyEndDate;
    /**
     * 实际结束日期
     */
    private LocalDate actualEndDate;
    /**
     * 使用次数限制
     */
    private Integer usageLimit;
    /**
     * 已使用次数
     */
    private Integer usedCount;
    /**
     * 状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 提交人
     */
    private Long submitUserId;
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    /**
     * 审批人
     */
    private Long approverUserId;
    /**
     * 审批意见
     */
    private String approveOpinion;
    /**
     * 审批时间
     */
    private LocalDateTime approveTime;
    /**
     * 撤回人
     */
    private Long recallUserId;
    /**
     * 撤回时间
     */
    private LocalDateTime recallTime;
    /**
     * BPM流程实例ID（预留扩展点）
     */
    private String processInstanceId;
    /**
     * 创建人
     */
    private Long creatorUserId;
    /**
     * 备注
     */
    private String remark;

}
