package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
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
@ProjectBusinessModel(ownerModule="SOL",entityType="siteSurvey",stableCode="SOL_SITE_SURVEY",name="现场工勘",permissionPrefix="pms:sol-site-survey",nativeEntityType="SITE_SURVEY")
public class SiteSurveyEntityDO extends BaseProjectBusinessEntity {

    @Override @JsonIgnore public Long getId() { return super.getId(); }
    @Override @JsonIgnore public Long getVersion() { return super.getVersion(); }
    /**
     * 所属项目编号
     */
    @Override @JsonIgnore public Long getProjectId() { return super.getProjectId(); }
    /**
     * 工勘编码，项目内唯一
     */
    @BusinessModelField(name="工勘编码", writable=false) @JsonIgnore private String code;
    /**
     * 工勘名称
     */
    @BusinessModelField(name="工勘名称") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) @JsonIgnore private String name;
    /**
     * 工勘日期
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="工勘日期") @JsonIgnore private LocalDate surveyDate;
    /**
     * 工勘责任人
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="工勘责任人") @JsonIgnore private Long surveyorUserId;
    /**
     * 工勘地点
     */
    @BusinessModelField(name="工勘地点") @JsonIgnore private String location;
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
    @BusinessModelField(name="地点状态", writable=false) @JsonIgnore private String locationResolutionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private String addressSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private String locationSnapshot;
    /**
     * 供电条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="供电条件") private String powerSupply;
    /**
     * 机柜条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="机柜条件") private String cabinet;
    /**
     * 网口条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="网口条件") private String networkPort;
    /**
     * 光纤条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="光纤条件") private String fiber;
    /**
     * 模块条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="模块条件") private String module;
    /**
     * 线缆条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="线缆条件") private String cable;
    /**
     * 接地条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="接地条件") private String ground;
    /**
     * 施工资源条件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="施工资源条件") private String constructionResource;
    /**
     * 工勘结论
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="工勘结论") private String conclusion;
    /**
     * 状态：0 草稿 1 已确认 2 已驳回 3 已归档
     */
    @BusinessModelField(name="状态", writable=false) @JsonIgnore private Integer status;
    /** Owner transition evidence, not generic update_time (editing a remark is not a new confirmation). */
    @JsonIgnore private java.time.LocalDateTime confirmedAt;
    @JsonIgnore private java.time.LocalDateTime archivedAt;
    /**
     * 备注
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="备注") private String remark;
    @BusinessModelField(name="是否委外") @JsonIgnore private Boolean outsourceRequired;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonIgnore private Long outsourceRequestId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="机柜就绪") private Boolean cabinetReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="线缆就绪") private Boolean cableReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="模块就绪") private Boolean moduleReady;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="原厂模块") private Boolean originalModule;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="厂商安装") private Boolean manufacturerInstallation;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="需要导轨托盘") private Boolean railTrayRequired;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="材料匹配") private Boolean materialMatches;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @BusinessModelField(name="要求结束日期", writable=false) private LocalDate requiredEndDate;

    @TableField(exist = false)
    @BusinessModelField(name="电源类型") private java.util.List<String> powerTypes;

    @TableField(exist = false)
    @BusinessModelField(name="供电环境") private java.util.List<String> powerEnvironments;

    @TableField(exist = false)
    @BusinessModelField(name="网络端口类型") private java.util.List<String> networkPortTypes;

    @TableField(exist = false)
    @BusinessModelField(name="选定材料") private java.util.List<SiteSurveyMaterialDO> selectedMaterials;
}
