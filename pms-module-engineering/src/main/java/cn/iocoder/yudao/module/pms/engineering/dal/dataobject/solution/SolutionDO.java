package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 实施方案 DO（FR-ENG-011 / FR-ENG-013）。
 * <p>
 * 对应表 {@code pms_eng_solution}。
 * 状态：0 草稿、1 已提交、2 审批中、3 已通过、4 已驳回、5 已撤回、6 已终止。
 */
@TableName("pms_eng_solution")
@Data
@EqualsAndHashCode(callSuper = true)
public class SolutionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 方案编码，项目内唯一
     */
    private String code;
    /**
     * 方案名称
     */
    private String name;
    /**
     * 方案类型
     */
    private String solutionType;
    /**
     * 关联文档模板ID（V36 结构化文档模板）
     */
    private Long templateId;
    /**
     * 关联模板版本ID（创建时锁定，模板变更不影响历史实例）
     */
    private Long templateVersionId;
    /**
     * 模板快照JSON（创建时的模板结构，不可变）
     */
    private String templateSnapshot;
    /**
     * 章节填写数据JSON（key=章节编码，value=章节内容）
     */
    private String sectionData;
    /**
     * 背景
     */
    private String background;
    /**
     * 目标
     */
    private String target;
    /**
     * 团队
     */
    private String team;
    /**
     * 清单
     */
    private String inventory;
    /**
     * 计划
     */
    private String plan;
    /**
     * 拓扑
     */
    private String topology;
    /**
     * 接口
     */
    private String interfacePlan;
    /**
     * IP
     */
    private String ipPlan;
    /**
     * 版本标签
     */
    private String versionLabel;
    /**
     * 脚本
     */
    private String script;
    /**
     * 质量
     */
    private String quality;
    /**
     * 风险
     */
    private String risk;
    /**
     * 运维
     */
    private String oAndM;
    /**
     * 审核级别 0 普通 1 重大
     */
    private Integer reviewLevel;
    /**
     * 状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止
     */
    private Integer status;
    /**
     * 审核人
     */
    private Long approvedBy;
    /**
     * 审核时间
     */
    private LocalDateTime approvedTime;
    /**
     * 审核意见
     */
    private String approvalOpinion;
    /**
     * 基线版本号（审核通过后冻结）
     */
    private Integer baselineVersion;
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
