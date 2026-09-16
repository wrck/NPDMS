package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeDataSource;

import java.sql.Connection;
import java.util.Map;

/**
 * 多数据源配置服务（批次3-T7）。
 *
 * <p>提供数据源配置 CRUD + 连接测试 + 同步到 DynamicDataSourceManager。
 * 支持三种集成模式：DIRECT / REPLICA / FEDERATED。</p>
 */
public interface LowCodeDataSourceService extends IService<LowCodeDataSource> {

    /**
     * 测试数据源连接。
     *
     * @param dataSource 数据源配置
     * @return 连接结果（success=true/false + message）
     */
    Map<String, Object> testConnection(LowCodeDataSource dataSource);

    /**
     * 激活数据源（注册到 DynamicDataSourceManager）。
     *
     * @param code 数据源编码
     */
    void activate(String code);

    /**
     * 停用数据源（从 DynamicDataSourceManager 注销）。
     *
     * @param code 数据源编码
     */
    void deactivate(String code);

    /**
     * 根据 code 获取已激活的数据源连接（DIRECT 模式）。
     *
     * @param code 数据源编码
     * @return JDBC 连接
     */
    Connection getConnection(String code);
}
