package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 现场工勘 DO（FR-ENG-001）。
 * <p>
 * 对应表 {@code sol_site_survey}。
 * 状态：0 草稿、1 已确认、2 已驳回、3 已归档。
 */
@TableName(value = "sol_site_survey", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyEntityDO extends BaseBusinessEntity {

    @Override @JsonIgnore public Long getId() { return super.getId(); }
    @Override @JsonIgnore public Long getVersion() { return super.getVersion(); }
    /**
     * 所属项目编号
     */
    @BusinessModelField @JsonIgnore private Long projectId;
    /**
     * 工勘编码，项目内唯一
     */
    @BusinessModelField @JsonIgnore private String code;
    /**
     * 工勘名称
     */
    @BusinessModelField @JsonIgnore private String name;
    /**
     * 工勘日期
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField @JsonIgnore private LocalDate surveyDate;
    /**
     * 工勘责任人
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField @JsonIgnore private Long surveyorUserId;
    /**
     * 工勘地点
     */
    @BusinessModelField @JsonIgnore private String location;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long addressId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long addressVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long siteId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long siteVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long siteLocationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long siteLocationVersion;
    @JsonIgnore private String locationResolutionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private String addressSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private String locationSnapshot;
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
    @BusinessModelField @JsonIgnore private Integer status;
    /** Owner transition evidence, not generic update_time (editing a remark is not a new confirmation). */
    @JsonIgnore private java.time.LocalDateTime confirmedAt;
    @JsonIgnore private java.time.LocalDateTime archivedAt;
    /**
     * 备注
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @BusinessModelField @JsonIgnore private Boolean outsourceRequired;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long outsourceRequestId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean cabinetReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean cableReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean moduleReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean originalModule;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean manufacturerInstallation;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean railTrayRequired;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean materialMatches;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requiredEndDate;

    @TableField(exist = false)
    private java.util.List<String> powerTypes;

    @TableField(exist = false)
    private java.util.List<String> powerEnvironments;

    @TableField(exist = false)
    private java.util.List<String> networkPortTypes;

    @TableField(exist = false)
    private java.util.List<SiteSurveyMaterialDO> selectedMaterials;
}
