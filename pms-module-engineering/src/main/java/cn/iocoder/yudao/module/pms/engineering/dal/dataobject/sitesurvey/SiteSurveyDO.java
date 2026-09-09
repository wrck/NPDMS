package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
@TableName(value = "pms_eng_site_survey", autoResultMap = true)
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate surveyDate;
    /**
     * 工勘责任人
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long surveyorUserId;
    /**
     * 工勘地点
     */
    private String location;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long addressId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer addressVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer siteVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long siteLocationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer siteLocationVersion;
    private String locationResolutionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String addressSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String locationSnapshot;
    /**
     * 供电条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String powerSupply;
    /**
     * 机柜条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cabinet;
    /**
     * 网口条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String networkPort;
    /**
     * 光纤条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fiber;
    /**
     * 模块条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String module;
    /**
     * 线缆条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cable;
    /**
     * 接地条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ground;
    /**
     * 施工资源条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String constructionResource;
    /**
     * 工勘结论
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conclusion;
    /**
     * 状态：0 草稿 1 已确认 2 已驳回 3 已归档
     */
    private Integer status;
    /**
     * 备注
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    private Long formRevisionId;
    private Integer formRevisionVersion;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class,
            updateStrategy = FieldStrategy.ALWAYS)
    private java.util.Map<String, Object> formExtraValues;

    private Boolean outsourceRequired;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long outsourceRequestId;
}
