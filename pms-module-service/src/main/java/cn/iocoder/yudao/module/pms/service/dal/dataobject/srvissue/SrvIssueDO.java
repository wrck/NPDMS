package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检问题与整改 DO
 */
@TableName("pms_srv_issue")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvIssueDO extends TenantBaseDO {

    /**
     * 问题编号
     */
    @TableId
    private Long id;
    /**
     * 所属巡检任务编号
     */
    private Long taskId;
    /**
     * 问题编码，任务内唯一
     */
    private String code;
    /**
     * 问题名称
     */
    private String name;
    /**
     * 问题描述
     */
    private String description;
    /**
     * 严重程度 H 高 / M 中 / L 低
     */
    private String severity;
    /**
     * 责任人
     */
    private Long ownerUserId;
    /**
     * 整改截止时间
     */
    private LocalDateTime deadline;
    /**
     * 整改方案
     */
    private String solution;
    /**
     * 验证结果
     */
    private String verifyResult;
    /**
     * 验证人
     */
    private Long verifiedBy;
    /**
     * 验证时间
     */
    private LocalDateTime verifiedTime;
    /**
     * 状态
     *
     * 枚举 0待分派 1已分派 2待验证 3已关闭 4已取消
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
