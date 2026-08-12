package cn.iocoder.yudao.module.pms.project.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目权威主数据 DO
 *
 * 字段来源：
 * - V5__pms_project_master_sync.sql：基础字段
 * - V7__pms_project_tree_and_team.sql：项目树相关字段（parent_id/root_id/path/depth/sort/category/major_project_flag/manager_user_id）
 */
@TableName("pms_project")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDO extends TenantBaseDO {

    /**
     * 项目编号
     */
    @TableId
    private Long id;
    /**
     * 全局唯一且不可复用的项目编码
     */
    private String code;
    /**
     * 项目名称
     */
    private String name;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 合同编码
     */
    private String contractCode;
    /**
     * 所属办公室编号
     */
    private Long officeId;
    /**
     * 销售人员编号
     */
    private Long salesUserId;
    /**
     * 行业
     */
    private String industry;
    /**
     * 实施方式
     */
    private String implementationMode;
    /**
     * 项目类型
     */
    private String projectType;
    /**
     * 出货状态
     */
    private String shipmentStatus;
    /**
     * 来源系统
     */
    private String sourceSystem;
    /**
     * 来源业务键
     */
    private String sourceBusinessKey;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 父项目编号，根项目为 null
     */
    private Long parentId;
    /**
     * 根项目编号，根项目为自身 id
     */
    private Long rootId;
    /**
     * 物化路径，格式 /{rootId}/.../{selfId}/
     */
    private String path;
    /**
     * 路径深度，根项目为 0
     */
    private Integer depth;
    /**
     * 同级排序号
     */
    private Integer sort;
    /**
     * 项目分类
     */
    private String category;
    /**
     * 是否重大项目
     */
    private Boolean majorProjectFlag;
    /**
     * 项目经理用户编号
     */
    private Long managerUserId;
    /**
     * 来源项目模板编号（仅记录，不外键约束）
     */
    private Long templateId;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
