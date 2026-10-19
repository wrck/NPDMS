package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Complete, append-only software registration; legacy backup attributes belong to formal extensions. */
@TableName("ast_device_software_version_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class DirectSoftwareHistoryRow extends TenantBaseDO {
    @TableId
    private Long id;
    private Long deviceId;
    private String deviceSn;
    private Long projectId;
    private Long previousRecordId;
    private String conpVersion;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
    private Boolean customized;
    private LocalDate releaseDate;
    private String versionDesc;
    private LocalDateTime recordedAt;
    private String dataSource;
    private Long parsedFactId;
    private String requestKey;
    private Long changeBatchId;
    private String contractNo;
    private String productCode;
    private LocalDate executeTime;
    private Integer dataState;
    private String customInfo;
}
