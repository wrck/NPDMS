package cn.iocoder.yudao.module.pms.lowcode.engine.ddl;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.module.pms.lowcode.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DDL 执行备份实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("low_ddl_backup")
public class DdlBackup extends BaseEntity {

    /** 实体 ID */
    private Long entityId;

    /** 实体编码 */
    private String entityCode;

    /** 物理表名 */
    private String tableName;

    /** 备份类型: CREATE/ALTER/DROP_COLUMN */
    private String backupType;

    /** 备份的 SQL（SHOW CREATE TABLE 结果） */
    private String backupSql;

    /** DROP COLUMN 时备份的列数据 JSON */
    private String backupData;

    /** 操作人 */
    private String operator;
}
