package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.business;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Default business contract over the existing imp_eng_configuration table. */
@TableName("imp_eng_configuration")
@Data
@EqualsAndHashCode(callSuper = true)
@ProjectBusinessModel(ownerModule = "IMP", entityType = "configuration", stableCode = "IMP_CONFIGURATION",
        name = "配置调试", permissionPrefix = "pms:imp-configuration", nativeEntityType = "CONFIGURATION")
public class ConfigurationBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name = "配置编码", displayOrder = 10, writable = false)
    private String code;
    @BusinessModelField(name = "关联设备", displayOrder = 20)
    @jakarta.validation.constraints.NotNull
    private Long equipmentId;
    @BusinessModelField(name = "配置日志", displayOrder = 30, writable = false)
    private String configLogUrl;
    @BusinessModelField(name = "导入日志文件", displayOrder = 40, writable = false)
    private Long importedLogFileId;
    @BusinessModelField(name = "导入日志批次", displayOrder = 50, writable = false)
    private Long importedLogBatchId;
    @BusinessModelField(name = "调试结果", displayOrder = 60)
    private String debugResult;
    @BusinessModelField(name = "调试人", displayOrder = 70)
    private Long debuggerUserId;
    @BusinessModelField(name = "调试时间", displayOrder = 80)
    private LocalDateTime debugTime;
    @BusinessModelField(name = "配置档案快照", displayOrder = 90)
    private String configSnapshot;
    @BusinessModelField(name = "状态", displayOrder = 100, writable = false)
    private Integer status;
    @BusinessModelField(name = "备注", displayOrder = 110)
    private String remark;
}

