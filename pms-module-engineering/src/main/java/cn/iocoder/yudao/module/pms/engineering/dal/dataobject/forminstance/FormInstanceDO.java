package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 准备数据表单实例 DO（FR-ENG-007）。
 * <p>
 * 对应表 {@code pms_eng_form_instance}。
 * 状态：0 待填、1 已填、2 已提交、3 已审核、4 已驳回。
 */
@TableName("pms_eng_form_instance")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormInstanceDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 实例编号（如 FI-2026-001），全局唯一
     */
    private String code;
    /**
     * 关联项目ID
     */
    private Long projectId;
    /**
     * 关联模板ID
     */
    private Long templateId;
    /**
     * 模板快照JSON（版本固定到实例）
     */
    private String templateSnapshot;
    /**
     * 填报数据JSON
     */
    private String formData;
    /**
     * 实例名称
     */
    private String name;
    /**
     * 状态：0 待填 1 已填 2 已提交 3 已审核 4 已驳回
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    /**
     * 审核人
     */
    private Long approverUserId;
    /**
     * 审核意见
     */
    private String approveOpinion;
    /**
     * 审核时间
     */
    private LocalDateTime approveTime;
    /**
     * 填报人
     */
    private Long fillerUserId;
    /**
     * 备注
     */
    private String remark;

}
