package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 实施问题与整改 DO
 */
@TableName("pms_eng_issue")
@Data
@EqualsAndHashCode(callSuper = true)
public class IssueDO extends TenantBaseDO {

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
     * 问题编码，项目内唯一
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
     * 来源 INSTALLATION 安装 / CONFIGURATION 配置 / JOINT_TEST 联调 / OTHER 其他
     */
    private String source;
    /**
     * 严重等级 1低 2中 3高
     */
    private Integer severity;
    /**
     * 责任人
     */
    private Long ownerUserId;
    /**
     * 整改时限
     */
    private LocalDateTime deadline;
    /**
     * 整改方案
     */
    private String solution;
    /**
     * 验证标准
     */
    private String verificationStandard;
    /**
     * 复测结果
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
     * 0待处理 1整改中 2待验证 3已关闭 4已挂起
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁
     */
    @Version
    private Integer version;

}
