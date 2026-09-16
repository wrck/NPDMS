package com.dp.plat.lowcode.engine.ddl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * DDL 执行日志 Mapper
 */
@Mapper
public interface DdlExecutionLogMapper extends BaseMapper<DdlExecutionLog> {
}
