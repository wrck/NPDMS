package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DDL 生成结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DdlResultDTO {
    private String tableName;
    private List<String> ddlStatements;
    private boolean hasJunctionTable;
    private String junctionTableDdl;
}
