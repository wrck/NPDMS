package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 需求分析与接口规划 DO（FR-ENG-004）。
 * <p>
 * 对应表 {@code pms_eng_requirement}。
 * 状态：0 草稿、1 已提交、2 已生效、3 已归档。
 */
@TableName("pms_eng_requirement")
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 需求编码，项目内唯一
     */
    private String code;
    /**
     * 需求名称
     */
    private String name;
    /**
     * 需求类型：BUSINESS 业务需求 / INTERFACE 接口规划
     */
    private String requirementType;
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
     * 项目背景目标
     */
    private String background;
    /**
     * 拓扑
     */
    private String topology;
    /**
     * 传输
     */
    private String transmission;
    /**
     * 流量
     */
    private String traffic;
    /**
     * 业务
     */
    private String business;
    /**
     * IP 规划
     */
    private String ipPlan;
    /**
     * 冗余
     */
    private String redundancy;
    /**
     * 防护
     */
    private String protection;
    /**
     * 运维
     */
    private String oAndM;
    /**
     * 日志留存
     */
    private String logRetention;
    /**
     * 接口关系内容（type=INTERFACE 时使用）
     */
    private String interfaceContent;
    /**
     * 状态：0 草稿 1 已提交 2 已生效 3 已归档
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
