package com.dp.plat.lowcode.engine.ddl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DDL 执行日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_ddl_execution_log")
public class DdlExecutionLog extends BaseEntity {

    /** 实体 ID */
    private Long entityId;

    /** 实体编码 */
    private String entityCode;

    /** 物理表名 */
    private String tableName;

    /** 执行类型: CREATE/ALTER/DROP_COLUMN/CREATE_INDEX/DROP_INDEX */
    private String executionType;

    /** 执行的 DDL SQL */
    private String ddlSql;

    /** 执行状态: SUCCESS/FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 操作人 */
    private String operator;
}
