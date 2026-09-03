package cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_migration_source_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class MigrationSourceRecordDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private String sourceSystem;
    private String sourceTable;
    private String sourceRecordKey;
    private String sourceBusinessKey;
    private String sourcePayload;
    private String sourceChecksum;
    private LocalDateTime extractedAt;
    @TableField(exist = false)
    private String resultType;
}
