package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 现场工勘 DO（FR-ENG-001）。
 * <p>
 * 对应表 {@code pms_eng_site_survey}。
 * 状态：0 草稿、1 已确认、2 已驳回、3 已归档。
 */
@TableName("pms_eng_site_survey")
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 工勘编码，项目内唯一
     */
    private String code;
    /**
     * 工勘名称
     */
    private String name;
    /**
     * 工勘日期
     */
    private LocalDate surveyDate;
    /**
     * 工勘责任人
     */
    private Long surveyorUserId;
    /**
     * 工勘地点
     */
    private String location;
    /**
     * 供电条件
     */
    private String powerSupply;
    /**
     * 机柜条件
     */
    private String cabinet;
    /**
     * 网口条件
     */
    private String networkPort;
    /**
     * 光纤条件
     */
    private String fiber;
    /**
     * 模块条件
     */
    private String module;
    /**
     * 线缆条件
     */
    private String cable;
    /**
     * 接地条件
     */
    private String ground;
    /**
     * 施工资源条件
     */
    private String constructionResource;
    /**
     * 工勘结论
     */
    private String conclusion;
    /**
     * 状态：0 草稿 1 已确认 2 已驳回 3 已归档
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
